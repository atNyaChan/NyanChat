package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelPrice
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.supportedBuiltInTools
import me.rerere.ai.provider.supportsQuota
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.account.preserveCredential
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ModelTypeTag
import me.rerere.rikkahub.ui.components.ai.ProviderQuotaText
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.BottomSheetTitle
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.ConnectedMultiChoiceButtons
import me.rerere.rikkahub.ui.components.ui.DeleteConfirmDialog
import me.rerere.rikkahub.ui.components.ui.HorizontalPager
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.bottomSheetMaxHeight
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.components.ui.rememberShareSheetState
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomRequestProperties
import me.rerere.rikkahub.ui.pages.setting.components.ProviderAccountConfig
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConnectionTester
import me.rerere.rikkahub.ui.pages.setting.components.SettingProviderQuotaQuery
import me.rerere.rikkahub.ui.pages.setting.components.convertTo
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import me.rerere.rikkahub.utils.formatContextLength
import me.rerere.ui.toast.ToastType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

// OpenAI 仅在 Responses API 下支持的内置工具，切换到 Chat Completions 时需要关闭
private val ResponsesApiOnlyBuiltInTools = setOf(BuiltInTools.Search, BuiltInTools.ImageGeneration)

private fun String.isValidBaseUrl(): Boolean = this.toHttpUrlOrNull() != null

@Composable
fun SettingProviderDetailSheet(
    id: Uuid,
    onDismiss: () -> Unit,
    isNew: Boolean = false,
    // 新增服务商时传入尚未写入设置的草稿，只有点击「添加」后才真正保存
    draftProvider: ProviderSetting? = null,
    initialModelId: Uuid? = null,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val shareSheetState = rememberShareSheetState()
    val scope = rememberCoroutineScope()
    val providerManager = koinInject<ProviderManager>()

    // 账号类草稿在登录时会被写入设置，之后以设置里的最新凭据为准
    val provider = settings.providers.find { it.id == id } ?: draftProvider ?: return

    var internalProvider by remember(provider) { mutableStateOf(provider) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val customModelDialog = useEditState<Model> { edited ->
        internalProvider = internalProvider.addModel(edited.copy(displayName = edited.displayName.trim()))
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
    val pagerState = rememberPagerState { ProviderSheetTab.entries.size }
    // 模型页的模型列表（拖拽排序需要 LazyListState）
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 模型卡片自带 key，按 key 定位下标而不是用 index，避免列表头部内容变化导致拖拽错位
        val models = internalProvider.models
        val fromIndex = models.indexOfFirst { it.id == from.key }
        if (fromIndex < 0) {
            return@rememberReorderableLazyListState
        }
        val toIndex = models.indexOfFirst { it.id == to.key }
            .takeIf { it >= 0 }
            ?: if (to.index < from.index) 0 else models.size - 1
        if (fromIndex != toIndex) {
            internalProvider = internalProvider.moveMove(fromIndex, toIndex)
        }
    }
    // 仅在打开时定位一次，避免排序等模型列表变化时反复滚动打断拖拽
    LaunchedEffect(initialModelId) {
        val initialIndex = internalProvider.models.indexOfFirst { it.id == initialModelId }
        if (initialIndex >= 0) {
            pagerState.scrollToPage(ProviderSheetTab.MODELS.ordinal)
            lazyListState.scrollToItem(MODELS_LIST_START_INDEX + initialIndex)
        }
    }

    val onSave = {
        val providerToSave: ProviderSetting = internalProvider.copyProvider(name = internalProvider.name.trim())
        vm.updateSettings { latest ->
            // 账号类草稿登录时已经写入设置，保存时按 id 更新，不能重复添加
            if (latest.providers.none { it.id == providerToSave.id }) {
                latest.copy(providers = listOf(providerToSave) + latest.providers)
            } else {
                latest.copy(
                    providers = latest.providers.map {
                        if (providerToSave.id == it.id) {
                            providerToSave.preserveCredential(it)
                        } else {
                            it
                        }
                    }
                )
            }
        }
        onDismiss()
    }

    val onDelete = {
        vm.updateSettings { latest ->
            latest.copy(providers = latest.providers.filter { it.id != internalProvider.id })
        }
        onDismiss()
    }

    val onMigrateModelId: (Model, Model) -> Unit = { sourceModel, targetModel ->
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
    }

    ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = {
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(bottom = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BottomSheetTitle(
                text = stringResource(
                    if (isNew) R.string.setting_provider_page_create_provider
                    else R.string.setting_provider_page_edit_provider
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            PagerTabs(
                pagerState = pagerState,
                tabs = ProviderSheetTab.entries,
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                // 页签内容从顶部起排，内容比抽屉矮时不要竖直居中
                verticalAlignment = Alignment.Top,
            ) { page ->
                when (ProviderSheetTab.entries[page]) {
                    // 配置页：除模型列表外的全部配置
                    ProviderSheetTab.CONFIG -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 账号类提供商没有可切换的类型，不显示选择器
                        if (!internalProvider.builtIn && internalProvider is ProviderSetting.ApiKey) {
                            ProviderTypeSelectorCard(
                                provider = internalProvider,
                                onEdit = { internalProvider = it },
                                // 提供商创建后类型仍可切换，但换类型会丢掉不兼容的配置
                                showWarning = !isNew,
                            )
                        }

                        val accountProvider = internalProvider as? ProviderSetting.Account
                        if (accountProvider != null) {
                            ProviderAccountConfig(
                                provider = accountProvider,
                                onEdit = { internalProvider = it },
                            )
                            ProviderConfigTogglesCardGroup(
                                provider = accountProvider,
                                onEdit = { internalProvider = it },
                            )
                        } else {
                            ProviderConfigFieldsCardGroup(
                                provider = internalProvider,
                                onEdit = { internalProvider = it },
                            )

                            ProviderConfigTogglesCardGroup(
                                provider = internalProvider,
                                onEdit = { internalProvider = it },
                            )

                            // 账号类的额度查询写死在 Provider 里，没有这一项
                            (internalProvider as? ProviderSetting.ApiKey)?.let { apiKeyProvider ->
                                SettingProviderQuotaQuery(
                                    provider = apiKeyProvider,
                                    // 额度按已保存的配置查，没开就不留这一行
                                    supportingContent = if (provider.supportsQuota) {
                                        { refreshKey ->
                                            ProviderQuotaText(
                                                providerSetting = provider,
                                                style = MaterialTheme.typography.labelSmall,
                                                refreshKey = refreshKey,
                                            )
                                        }
                                    } else null,
                                    onEdit = { internalProvider = apiKeyProvider.withQuotaQuery(it) },
                                )
                            }
                        }

                        CustomRequestProperties(
                            headers = internalProvider.customHeaders,
                            onUpdateHeaders = { headers ->
                                internalProvider = internalProvider.copyProvider(customHeaders = headers)
                            },
                        )
                    }

                    // 模型页：模型操作行与模型列表
                    ProviderSheetTab.MODELS -> LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = lazyListState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (internalProvider.models.isEmpty()) {
                            // 空状态占满整块区域并在其中居中，顶部仍保留模型操作行
                            item {
                                Box(modifier = Modifier.fillParentMaxSize()) {
                                    ModelsHeaderRow(
                                        modelCount = internalProvider.models.size,
                                        onAddCustomModel = { customModelDialog.open(Model()) },
                                        onSelectModels = { showModelPicker = true },
                                    )
                                    ExtensionEmptyState(
                                        message = stringResource(R.string.setting_provider_page_no_models),
                                        description = stringResource(R.string.setting_provider_page_add_models_hint),
                                        icon = HugeIcons.Package01,
                                    )
                                }
                            }
                        } else {
                            item {
                                ModelsHeaderRow(
                                    modelCount = internalProvider.models.size,
                                    onAddCustomModel = { customModelDialog.open(Model()) },
                                    onSelectModels = { showModelPicker = true },
                                )
                            }
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
                                        onMigrateModelId = onMigrateModelId,
                                        modifier = longPressReorder(isDragging),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        showDeleteDialog = true
                    },
                    // 新增服务商尚未保存，无需提供删除
                    enabled = !internalProvider.builtIn && !isNew,
                ) {
                    Icon(HugeIcons.Delete01, null)
                }
                ProviderConnectionTester(
                    internalProvider = internalProvider,
                )
                // 账号型提供商离开账号就没法用，不提供分享
                if (internalProvider !is ProviderSetting.Account) {
                    IconButton(
                        onClick = {
                            shareSheetState.show(internalProvider)
                        }
                    ) {
                        Icon(HugeIcons.Share01, null)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = onSave,
                ) {
                    Text(stringResource(if (isNew) R.string.common_add else R.string.common_save))
                }
            }
        }
    }

    ShareSheet(shareSheetState)

    DeleteConfirmDialog(
        show = showDeleteDialog,
        type = stringResource(R.string.delete_type_provider),
        onConfirm = {
            showDeleteDialog = false
            onDelete()
        },
        onDismiss = { showDeleteDialog = false },
    )

    if (showModelPicker) {
        ModelPickerSheet(
            models = modelList,
            selectedModels = internalProvider.models,
            provider = internalProvider,
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
            onDismiss = { showModelPicker = false },
            onMigrateModelId = onMigrateModelId,
        )
    }

    if (customModelDialog.isEditing) {
        customModelDialog.currentState?.let { modelState ->
            val sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
            ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface,
                onDismissRequest = {
                    customModelDialog.dismiss()
                },
                sheetState = sheetState,
                dragHandle = {
                    BottomSheetDefaults.DragHandle()
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BottomSheetTitle(
                        text = stringResource(R.string.setting_provider_page_custom_model),
                        modifier = Modifier.padding(horizontal = 24.dp),
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
                            .padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                customModelDialog.dismiss()
                            },
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
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

// 模型页列表开头是「模型（数量）」标题行（拖拽排序按 key 定位，此常量仅用于初始定位滚动）
private const val MODELS_LIST_START_INDEX = 1

// 模型页顶部的「模型（数量）」操作行：左侧标题，右侧“自定义模型”与“选择模型”按钮
@Composable
private fun ModelsHeaderRow(
    modelCount: Int,
    onAddCustomModel: () -> Unit,
    onSelectModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.setting_provider_page_models_format,
                modelCount,
            ),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onAddCustomModel,
            ) {
                Icon(
                    HugeIcons.Add01,
                    stringResource(R.string.setting_provider_page_custom_model)
                )
            }
            IconButton(
                onClick = onSelectModels,
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

// 提供商编辑抽屉的页签：配置页放除模型列表外的全部配置，模型页放模型操作行与模型列表
private enum class ProviderSheetTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    CONFIG(HugeIcons.Settings03, R.string.setting_provider_page_tab_config),
    MODELS(HugeIcons.Package01, R.string.setting_provider_page_tab_models);

    override val label: String
        @Composable get() = stringResource(labelRes)
}

@Composable
private fun ProviderTypeSelectorCard(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
    showWarning: Boolean = false,
) {
    CardGroup(modifier = Modifier.fillMaxWidth()) {
        item(
            headlineContent = {},
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectedChoiceButtons(
                        options = ProviderSetting.Types,
                        selected = provider::class,
                        onSelect = { onEdit(provider.convertTo(it)) },
                    ) { type, selected ->
                        ConnectedChoiceLabel(text = type.simpleName ?: "", selected = selected)
                    }
                    if (showWarning) {
                        Text(
                            text = stringResource(R.string.common_not_recommended_to_modify),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

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
                                onValueChange = { onEdit(provider.copy(name = it)) },
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
                            OutlinedTextField(
                                value = if (provider.useResponseApi) provider.responsesPath else provider.chatCompletionsPath,
                                onValueChange = {
                                    onEdit(
                                        if (provider.useResponseApi) {
                                            provider.copy(responsesPath = it.trim())
                                        } else {
                                            provider.copy(chatCompletionsPath = it.trim())
                                        }
                                    )
                                },
                                label = { Text(stringResource(R.string.setting_provider_page_api_path)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !provider.builtIn,
                            )
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
                                onValueChange = { onEdit(provider.copy(name = it)) },
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
                                onValueChange = { onEdit(provider.copy(name = it)) },
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

            // 账号类没有 API Key、地址等字段，名称与登录信息在 ProviderAccountConfig 里
            is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> Unit
        }
    }
}

@Composable
private fun ProviderConfigTogglesCardGroup(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
) {
    CardGroup(modifier = Modifier.fillMaxWidth()) {
        when (provider) {
            is ProviderSetting.OpenAI -> {
                switchItem(
                    checked = provider.enabled,
                    onCheckedChange = { onEdit(provider.copy(enabled = it)) },
                    headlineContent = { Text(stringResource(R.string.setting_provider_page_enable)) },
                )

                switchItem(
                    checked = provider.useResponseApi,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onEdit(provider.copy(useResponseApi = true))
                        } else {
                            // 切到 Chat Completions：关闭仅 Responses API 支持的内置工具
                            onEdit(
                                provider.copy(
                                    useResponseApi = false,
                                    models = provider.models.map { model ->
                                        model.copy(tools = model.tools - ResponsesApiOnlyBuiltInTools)
                                    }
                                )
                            )
                        }
                    },
                    supportingContent = {
                        if (provider.baseUrl.toHttpUrlOrNull()?.host?.let {
                                it != "api.openai.com" && it != "api.deepseek.com" && it != "openrouter.ai"
                            } == true
                        ) {
                            Text(
                                text = stringResource(R.string.setting_provider_page_response_api_not_compatible),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.setting_provider_page_response_api)) },
                )

            }

            is ProviderSetting.Google -> {
                switchItem(
                    checked = provider.enabled,
                    onCheckedChange = { onEdit(provider.copy(enabled = it)) },
                    headlineContent = { Text(stringResource(R.string.setting_provider_page_enable)) },
                )

                switchItem(
                    checked = provider.vertexAI,
                    onCheckedChange = { onEdit(provider.copy(vertexAI = it)) },
                    headlineContent = { Text(stringResource(R.string.setting_provider_page_vertex_ai)) },
                )

                if (provider.vertexAI) {
                    switchItem(
                        checked = provider.useServiceAccount,
                        onCheckedChange = { onEdit(provider.copy(useServiceAccount = it)) },
                        headlineContent = { Text(stringResource(R.string.setting_provider_page_use_service_account)) },
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
                switchItem(
                    checked = provider.enabled,
                    onCheckedChange = { onEdit(provider.copy(enabled = it)) },
                    headlineContent = { Text(stringResource(R.string.setting_provider_page_enable)) },
                )
            }

            is ProviderSetting.ChatGPT, is ProviderSetting.Grok -> {
                switchItem(
                    checked = provider.enabled,
                    onCheckedChange = { onEdit(provider.copyProvider(enabled = it)) },
                    headlineContent = { Text(stringResource(R.string.setting_provider_page_enable)) },
                )
            }
        }
    }
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
    provider: ProviderSetting,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onDismiss: () -> Unit,
    onMigrateModelId: ((Model, Model) -> Unit)? = null,
) {
    ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
    ) {
        var filterText by remember { mutableStateOf("") }
        var deleteTarget by remember { mutableStateOf<Model?>(null) }
        var showDeleteDialog by remember { mutableStateOf(false) }
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
                .bottomSheetMaxHeight()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 内容左右 8dp，再补 16dp 共 24dp
            BottomSheetTitle(
                text = stringResource(R.string.model_list_select_model),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // 可选模型标题
            Text(
                text = stringResource(R.string.setting_provider_page_avaliable_models),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
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
                                        deleteTarget = selectedModels.firstOrNull { model -> model.modelId == it.modelId }
                                            ?: it
                                        showDeleteDialog = true
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

        // 与模型设置页删除模型一致的确认弹窗；保持挂载以便其中的迁移列表在关闭确认后仍可用
        deleteTarget?.let { modelToDelete ->
            ModelDeleteDialog(
                show = showDeleteDialog,
                model = modelToDelete,
                parentProvider = provider,
                onConfirm = {
                    showDeleteDialog = false
                    onModelDeselected(modelToDelete)
                },
                onDismiss = { showDeleteDialog = false },
                onMigrateModelId = onMigrateModelId,
            )
        }
    }
}

private data class ModelFormTab(
    override val icon: ImageVector,
    @param:StringRes private val labelRes: Int,
) : PagerTab {
    override val label: String
        @Composable get() = stringResource(labelRes)
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
    // 内置工具只对部分提供商 API 生效（如 OpenAI 仅 Responses API），均不支持时隐藏整个卡片
    val effectiveProvider = model.providerOverwrite ?: parentProvider
    val supportedTools = effectiveProvider?.supportedBuiltInTools().orEmpty()
    val modelFormTabs = buildList<PagerTab> {
        add(ModelFormTab(HugeIcons.Settings03, R.string.setting_provider_page_basic_settings))
        add(ModelFormTab(HugeIcons.Tools, R.string.setting_provider_page_advanced_settings))
    }
    val pagerState = rememberPagerState { modelFormTabs.size }
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
        // 仅当模型名称仍然跟随模型 ID（未自定义或为空）时同步更新名称
        val displayNameFollowsId = model.displayName.isBlank() || model.displayName == model.modelId
        onModelChange(
            model.copy(
                modelId = id,
                displayName = if (displayNameFollowsId) id else model.displayName,
                inputModalities = inputModality,
                outputModalities = outputModality,
                abilities = abilities
            )
        )
    }

    Column {
        PagerTabs(
            pagerState = pagerState,
            tabs = modelFormTabs,
        )

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
                                                setModelId(it.trim())
                                            },
                                            label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = {
                                                Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
                                            },
                                            supportingText = {
                                                if (isEdit) {
                                                    Text(stringResource(R.string.common_not_recommended_to_modify))
                                                }
                                            }
                                        )

                                        OutlinedTextField(
                                            value = model.displayName,
                                            onValueChange = {
                                                onModelChange(model.copy(displayName = it))
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
                        }

                        CardGroup(
                            modifier = Modifier.fillMaxWidth()
                        ) {
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

                                item(
                                    supportingContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ModelContextLengthSelector(
                                                contextLength = model.contextLength,
                                                onUpdateContextLength = {
                                                    onModelChange(model.copy(contextLength = it))
                                                }
                                            )
                                        }
                                    },
                                    headlineContent = {},
                                )
                            }
                        }

                        CardGroup(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            switchItem(
                                checked = model.price != null,
                                onCheckedChange = { enabled ->
                                    onModelChange(model.copy(price = if (enabled) ModelPrice() else null))
                                },
                                headlineContent = {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.setting_provider_page_set_price))
                                        Text(
                                            text = stringResource(R.string.setting_provider_page_price_unit),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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
                                    showDeleteDialog = true
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
                        // 内置工具只对部分提供商 API 生效，均不支持时不显示该卡片
                        if (supportedTools.isNotEmpty()) {
                            CardGroup(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item(
                                    supportingContent = {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                            BuiltInToolsSwitches(
                                                tools = model.tools,
                                                supportedTools = supportedTools,
                                                onUpdateTools = { tools ->
                                                    onModelChange(model.copy(tools = tools))
                                                },
                                            )
                                        }
                                    },
                                    headlineContent = {},
                                )
                            }
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

                        // 覆盖配置是提供商的独立副本，账号凭据不能复制出去，账号型提供商不支持覆盖
                        if (parentProvider !is ProviderSetting.Account) {
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
                                                    // 覆盖提供商切到 Chat Completions 时，关闭仅 Responses API 支持的内置工具
                                                    val tools =
                                                        if (providerOverride is ProviderSetting.OpenAI && !providerOverride.useResponseApi) {
                                                            model.tools - ResponsesApiOnlyBuiltInTools
                                                        } else {
                                                            model.tools
                                                        }
                                                    onModelChange(
                                                        model.copy(
                                                            providerOverwrite = providerOverride,
                                                            tools = tools,
                                                        )
                                                    )
                                                },
                                                parentProvider = parentProvider
                                            )
                                        }
                                    },
                                    headlineContent = {},
                                )
                            }
                        }

                        CustomRequestProperties(
                            headers = model.customHeaders,
                            onUpdateHeaders = { headers ->
                                onModelChange(model.copy(customHeaders = headers))
                            },
                            customBodies = model.customBodies,
                            onUpdateCustomBodies = { bodies ->
                                onModelChange(model.copy(customBodies = bodies))
                            },
                        )
                    }
                }
            }
        }
    }

    migrationTarget?.let { targetModel ->
        ModelMigrationConfirmDialog(
            model = model,
            targetModel = targetModel,
            parentProvider = parentProvider,
            providers = settings.providers,
            onDismiss = { migrationTarget = null },
            onConfirm = {
                migrationTarget = null
                onMigrateModelId?.invoke(model, targetModel)
            },
        )
    }

    ModelDeleteDialog(
        show = showDeleteDialog,
        model = model,
        parentProvider = parentProvider,
        onConfirm = {
            showDeleteDialog = false
            onDeleteModel?.invoke()
        },
        onDismiss = { showDeleteDialog = false },
        onMigrateModelId = onMigrateModelId,
    )
}

/**
 * 删除模型的确认弹窗，与「选择模型」抽屉共用。
 *
 * 内部处理被引用消息数量的统计，以及删除前的模型 ID 迁移入口。
 */
@Composable
private fun ModelDeleteDialog(
    show: Boolean,
    model: Model,
    parentProvider: ProviderSetting?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onMigrateModelId: ((Model, Model) -> Unit)? = null,
) {
    val settingsStore = koinInject<me.rerere.rikkahub.data.datastore.SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val conversationRepository = koinInject<me.rerere.rikkahub.data.repository.ConversationRepository>()
    val navController = LocalNavController.current

    var referencedMessageCount by remember(model.id) { mutableStateOf(0) }
    var migrationTarget by remember { mutableStateOf<Model?>(null) }
    val migrationModelListState = rememberModelListState(
        modelId = null,
        providers = settings.providers,
        type = model.type,
    )

    LaunchedEffect(show, model.id) {
        if (show) {
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

    migrationTarget?.let { targetModel ->
        ModelMigrationConfirmDialog(
            model = model,
            targetModel = targetModel,
            parentProvider = parentProvider,
            providers = settings.providers,
            onDismiss = { migrationTarget = null },
            onConfirm = {
                migrationTarget = null
                onMigrateModelId?.invoke(model, targetModel)
            },
        )
    }

    val referencedMessagesLink = stringResource(
        R.string.setting_provider_page_delete_model_referenced_messages,
        referencedMessageCount,
    )
    val warningSuffix = stringResource(R.string.setting_provider_page_delete_model_warning_suffix)
    val migrateLink = stringResource(R.string.setting_provider_page_delete_model_migrate_link)
    val warningEnd = stringResource(R.string.setting_provider_page_delete_model_warning_end)
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    )

    DeleteConfirmDialog(
        show = show,
        type = stringResource(R.string.delete_type_model),
        name = model.displayName,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        extra = {
            if (referencedMessageCount > 0) {
                Text(
                    buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "search_model_messages",
                                styles = linkStyle,
                                linkInteractionListener = {
                                    onDismiss()
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
                                    onDismiss()
                                    migrationModelListState.open()
                                },
                            )
                        ) {
                            append(migrateLink)
                        }
                        append(warningEnd)
                    }
                )
            }
        },
    )
}

/** 迁移模型 ID 前的确认弹窗。 */
@Composable
private fun ModelMigrationConfirmDialog(
    model: Model,
    targetModel: Model,
    parentProvider: ProviderSetting?,
    providers: List<ProviderSetting>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_provider_page_migrate_id_confirm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.setting_provider_page_migrate_id_confirm_desc,
                    model.displayName,
                    model.findProvider(providers, checkOverwrite = false)?.name
                        ?: parentProvider?.name.orEmpty(),
                    targetModel.displayName,
                    targetModel.findProvider(providers, checkOverwrite = false)?.name.orEmpty(),
                )
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_confirm_action))
            }
        },
    )
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
    ConnectedChoiceButtons(
        options = ModelType.entries,
        selected = selectedType,
        onSelect = onTypeSelected,
    ) { type, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                }
            ),
            selected = selected,
        )
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
    ConnectedMultiChoiceButtons(
        options = Modality.entries,
        selected = inputModalities,
        onToggle = { modality, checked ->
            onUpdateInputModalities(if (checked) inputModalities + modality else inputModalities - modality)
        },
    ) { modality, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (modality) {
                    Modality.TEXT -> R.string.setting_provider_page_text
                    Modality.IMAGE -> R.string.setting_provider_page_image
                }
            ),
            selected = selected,
            icon = HugeIcons.Tick01,
        )
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
    ConnectedMultiChoiceButtons(
        options = Modality.entries,
        selected = outputModalities,
        onToggle = { modality, checked ->
            onUpdateOutputModalities(if (checked) outputModalities + modality else outputModalities - modality)
        },
    ) { modality, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (modality) {
                    Modality.TEXT -> R.string.setting_provider_page_text
                    Modality.IMAGE -> R.string.setting_provider_page_image
                }
            ),
            selected = selected,
            icon = HugeIcons.Tick01,
        )
    }
}

// 常见的上下文窗口大小，其余的走自定义输入
private val ContextLengthPresets = listOf(128_000, 200_000, 1_000_000)

@Composable
private fun ModelContextLengthSelector(
    contextLength: Int,
    onUpdateContextLength: (Int) -> Unit
) {
    var custom by remember { mutableStateOf(contextLength !in ContextLengthPresets) }
    // 输入过程中允许为空，只有合法的正整数才写回模型
    var customText by remember { mutableStateOf(contextLength.toString()) }

    Text(
        stringResource(R.string.setting_provider_page_context_length),
        style = MaterialTheme.typography.titleSmall
    )
    // null 表示自定义
    ConnectedChoiceButtons(
        options = ContextLengthPresets + null,
        selected = if (custom) null else contextLength,
        onSelect = { preset ->
            custom = preset == null
            if (preset != null) {
                customText = preset.toString()
                onUpdateContextLength(preset)
            }
        },
    ) { preset, selected ->
        ConnectedChoiceLabel(
            text = preset?.formatContextLength() ?: stringResource(R.string.setting_provider_page_context_length_custom),
            selected = selected,
        )
    }
    AnimatedVisibility(visible = custom) {
        OutlinedTextField(
            value = customText,
            onValueChange = { value ->
                customText = value.filter { it.isDigit() }
                customText.toIntOrNull()?.takeIf { it > 0 }?.let(onUpdateContextLength)
            },
            modifier = Modifier.fillMaxWidth(),
            suffix = { Text("tokens") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = (customText.toIntOrNull() ?: 0) <= 0,
        )
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
    ConnectedMultiChoiceButtons(
        options = ModelAbility.entries,
        selected = abilities,
        onToggle = { ability, checked ->
            onUpdateAbilities(if (checked) abilities + ability else abilities - ability)
        },
    ) { ability, selected ->
        ConnectedChoiceLabel(
            text = stringResource(
                when (ability) {
                    ModelAbility.TOOL -> R.string.setting_provider_page_tool
                    ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                }
            ),
            selected = selected,
            icon = HugeIcons.Tick01,
        )
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
        onEdit(it.copy(displayName = it.displayName.trim()))
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
            ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface,
                onDismissRequest = {
                    dialogState.dismiss()
                },
                sheetState = sheetState,
                dragHandle = {
                    BottomSheetDefaults.DragHandle()
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(bottom = 8.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BottomSheetTitle(
                        text = stringResource(R.string.setting_provider_page_edit_model),
                        modifier = Modifier.padding(horizontal = 24.dp),
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
                            .padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                dialogState.dismiss()
                            },
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = {
                                if (editingModel.displayName.isNotBlank()) {
                                    dialogState.confirm()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.common_save))
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
private fun BuiltInToolsSwitches(
    tools: Set<BuiltInTools>,
    supportedTools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit,
) {
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
    ).filter { (tool, _) -> tool in supportedTools }
    // 开关组与所在卡片拉开层次，用页面同款的 surface 背景色
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)

    CardGroup(
        modifier = Modifier.fillMaxWidth(),
    ) {
        availableTools.forEach { (tool, info) ->
            val (title, description) = info
            switchItem(
                checked = tool in tools,
                onCheckedChange = { checked ->
                    if (checked) {
                        onUpdateTools(tools + tool)
                    } else {
                        onUpdateTools(tools - tool)
                    }
                },
                colors = itemColors,
                supportingContent = { Text(description) },
                headlineContent = { Text(title) },
            )
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
    var isCreating by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (providerOverride != null) {
            OutlinedItemCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    editingProvider = providerOverride
                    isCreating = false
                    showProviderConfig = true
                },
                colors = CardDefaults.outlinedCardColors(),
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
                    isCreating = true
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
            ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface,
                onDismissRequest = {
                    showProviderConfig = false
                    editingProvider = null
                },
                sheetState = rememberBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
                ),
                dragHandle = {
                    BottomSheetDefaults.DragHandle()
                },
            ) {
                var internalProvider by remember(editingProvider) { mutableStateOf(editingProvider!!) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(bottom = 8.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BottomSheetTitle(
                        text = stringResource(R.string.setting_provider_page_configure_provider_override),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!internalProvider.builtIn && internalProvider is ProviderSetting.ApiKey) {
                            ProviderTypeSelectorCard(
                                provider = internalProvider,
                                onEdit = { internalProvider = it },
                            )
                        }

                        ProviderConfigFieldsCardGroup(
                            provider = internalProvider,
                            onEdit = { internalProvider = it },
                        )

                        ProviderConfigTogglesCardGroup(
                            provider = internalProvider,
                            onEdit = { internalProvider = it },
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(
                            onClick = {
                                onUpdateProviderOverride(internalProvider.copyProvider(name = internalProvider.name.trim()))
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (isCreating) R.string.common_add else R.string.common_save
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

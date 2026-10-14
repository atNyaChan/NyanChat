package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Connect
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.RemoteFileStore
import me.rerere.rikkahub.ui.components.ai.ExtensionEmptyState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.components.ui.s3ConnectionItems
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.components.MediaGenerationProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.ProviderEditSheet
import me.rerere.rikkahub.ui.pages.setting.components.label
import me.rerere.rikkahub.ui.pages.setting.components.typeName
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class MediaTab(
    override val icon: ImageVector,
    @param:StringRes override val label: Int,
) : PagerTab {
    PROVIDERS(HugeIcons.Image02, R.string.setting_media_page_providers),
    STORAGE(HugeIcons.Database02, R.string.setting_media_page_asset_storage),
}

@Composable
fun SettingMediaPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingProvider by remember { mutableStateOf<MediaGenerationProviderSetting?>(null) }
    var addingProvider by remember { mutableStateOf<MediaGenerationProviderSetting?>(null) }
    val pagerState = rememberPagerState { MediaTab.entries.size }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_media))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (MediaTab.entries[pagerState.targetPage] == MediaTab.PROVIDERS) {
                        IconButton(onClick = { addingProvider = MediaGenerationProviderSetting.OpenAI() }) {
                            Icon(HugeIcons.Add01, stringResource(R.string.setting_media_page_add_provider))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            PagerTabs(
                pagerState = pagerState,
                tabs = MediaTab.entries,
                modifier = Modifier.padding(bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (MediaTab.entries[page]) {
                    MediaTab.PROVIDERS -> MediaProviderList(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                        onEdit = { editingProvider = it },
                    )

                    MediaTab.STORAGE -> MediaUploadS3Tab(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                    )
                }
            }
        }
    }

    editingProvider?.let { provider ->
        MediaProviderSheet(
            title = stringResource(R.string.setting_media_page_edit_provider),
            initial = provider,
            confirmText = stringResource(R.string.common_save),
            onConfirm = { edited ->
                vm.updateSettings { latest ->
                    latest.copy(
                        mediaGenerationProviders = latest.mediaGenerationProviders.map {
                            if (it.id == provider.id) edited else it
                        }
                    )
                }
            },
            onDelete = {
                vm.updateSettings { latest ->
                    latest.copy(
                        mediaGenerationProviders = latest.mediaGenerationProviders.filter { it.id != provider.id }
                    )
                }
                editingProvider = null
            },
            onDismiss = { editingProvider = null }
        )
    }

    addingProvider?.let { provider ->
        MediaProviderSheet(
            title = stringResource(R.string.setting_media_page_add_provider),
            initial = provider,
            confirmText = stringResource(R.string.common_add),
            onConfirm = { added ->
                vm.updateSettings {
                    it.copy(mediaGenerationProviders = listOf(added) + it.mediaGenerationProviders)
                }
            },
            onDismiss = { addingProvider = null }
        )
    }
}

@Composable
private fun MediaProviderList(
    settings: Settings,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    onEdit: (MediaGenerationProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.mediaGenerationProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onUpdateSettings { it.copy(mediaGenerationProviders = newProviders) }
    }

    if (settings.mediaGenerationProviders.isEmpty()) {
        ExtensionEmptyState(
            message = stringResource(R.string.setting_media_page_empty),
            icon = HugeIcons.Image02,
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            items(settings.mediaGenerationProviders, key = { it.id }) { provider ->
                ReorderableItem(
                    state = reorderableState,
                    key = provider.id
                ) { isDragging ->
                    MediaProviderItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(longPressReorder(isDragging)),
                        provider = provider,
                        onEdit = { onEdit(provider) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaProviderItem(
    provider: MediaGenerationProviderSetting,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit
) {
    OutlinedItemCard(
        onClick = onEdit,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AutoAIIcon(
                    name = provider.name.ifEmpty { provider.typeName },
                    modifier = Modifier.size(32.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = provider.name.ifEmpty { provider.typeName },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = provider.typeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MediaKind.entries
                            .map { kind -> kind to provider.models.count { it.kind == kind } }
                            .filter { it.second > 0 }
                            .forEach { (kind, count) ->
                                Tag {
                                    Text("${kind.label} $count")
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaProviderSheet(
    title: String,
    initial: MediaGenerationProviderSetting,
    confirmText: String,
    onConfirm: (MediaGenerationProviderSetting) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var showDeleteConfirm by remember(initial) { mutableStateOf(false) }

    ProviderEditSheet(
        title = title,
        initial = initial,
        confirmText = confirmText,
        onConfirm = { provider ->
            // 没填模型 ID 的行不保存
            onConfirm(provider.copyProvider(models = provider.models.filter { it.modelId.isNotBlank() }))
        },
        onDismiss = onDismiss,
    ) { provider, onValueChange, modifier ->
        MediaGenerationProviderConfigure(
            setting = provider,
            onValueChange = onValueChange,
            modifier = modifier,
            footer = if (onDelete != null) {
                {
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
                            stringResource(R.string.common_delete),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            } else {
                null
            },
        )
    }

    RikkaConfirmDialog(
        show = showDeleteConfirm,
        title = stringResource(R.string.setting_media_page_delete_provider),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            showDeleteConfirm = false
            onDelete?.invoke()
        },
        onDismiss = { showDeleteConfirm = false },
    ) {
        Text(
            if (initial.name.isBlank()) {
                stringResource(R.string.setting_media_page_delete_unnamed_provider_confirm)
            } else {
                stringResource(R.string.setting_media_page_delete_provider_confirm, initial.name)
            }
        )
    }
}

@Composable
private fun MediaUploadS3Tab(
    settings: Settings,
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    modifier: Modifier = Modifier,
    remoteFileStore: RemoteFileStore = koinInject(),
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }

    // 输入框以本地状态为准：设置要经过存储再回流，直接绑定的话打字快了会被旧值覆盖
    var config by remember { mutableStateOf(settings.uploadS3Config) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.setting_media_page_s3_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        CardGroup {
            s3ConnectionItems(
                config = config,
                onUpdate = {
                    config = it
                    onUpdateSettings { latest -> latest.copy(uploadS3Config = it) }
                }
            )
        }

        CardGroup {
            item(
                onClick = if (!testing) {
                    {
                        scope.launch {
                            testing = true
                            remoteFileStore.testConnection()
                                .onSuccess {
                                    toaster.show(
                                        context.getString(R.string.backup_page_connection_success),
                                        type = ToastType.Success
                                    )
                                }
                                .onFailure {
                                    toaster.show(
                                        context.getString(R.string.backup_page_connection_failed, it.message ?: ""),
                                        type = ToastType.Error
                                    )
                                }
                            testing = false
                        }
                    }
                } else null,
                headlineContent = { Text(stringResource(R.string.backup_page_test_connection)) },
                leadingContent = {
                    if (testing) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        CardGroupIcon(HugeIcons.Connect)
                    }
                },
            )
        }
    }
}

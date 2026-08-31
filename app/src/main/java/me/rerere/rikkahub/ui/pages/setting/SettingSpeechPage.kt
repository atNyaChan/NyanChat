package me.rerere.rikkahub.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.VolumeHigh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.datastore.DEFAULT_SYSTEM_TTS_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.pages.setting.components.ASRProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderConfigure
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch

@Composable
fun SettingSpeechPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingTTSProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    var editingASRProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (pagerState.currentPage == 0) R.string.speech_tab_tts else R.string.speech_tab_asr
                        )
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if (pagerState.currentPage == 0) {
                        AddTTSProviderButton {
                            vm.updateSettings(
                                settings.copy(
                                    ttsProviders = listOf(it) + settings.ttsProviders
                                )
                            )
                        }
                    } else {
                        AddASRProviderButton {
                            vm.updateSettings(
                                settings.copy(
                                    asrProviders = listOf(it) + settings.asrProviders,
                                    selectedASRProviderId = settings.selectedASRProviderId ?: it.id
                                )
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(HugeIcons.VolumeHigh, contentDescription = null) },
                    label = { Text(stringResource(R.string.speech_tab_tts)) }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(HugeIcons.Mic01, contentDescription = null) },
                    label = { Text(stringResource(R.string.speech_tab_asr)) }
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> Column(modifier = Modifier.fillMaxSize()) {
                    TTSProviderList(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                        onEdit = { editingTTSProvider = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                1 -> ASRProviderList(
                    settings = settings,
                    onUpdateSettings = vm::updateSettings,
                    onEdit = { editingASRProvider = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Edit TTS Provider Bottom Sheet
    editingTTSProvider?.let { provider ->
        val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        var currentProvider by remember(provider) { mutableStateOf(provider) }
        var showDeleteConfirm by remember(provider.id) { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = {
                editingTTSProvider = null
            },
            sheetState = bottomSheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_tts_page_edit_provider),
                    style = MaterialTheme.typography.headlineSmall
                )

                TTSProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f),
                    footer = if (provider.id != DEFAULT_SYSTEM_TTS_ID) {
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
                                    stringResource(R.string.setting_tts_page_delete_provider),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            editingTTSProvider = null
                        }
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    TextButton(
                        onClick = {
                            val newProviders = settings.ttsProviders.map {
                                if (it.id == provider.id) currentProvider else it
                            }
                            vm.updateSettings(settings.copy(ttsProviders = newProviders))
                            editingTTSProvider = null
                        }
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
        RikkaConfirmDialog(
            show = showDeleteConfirm,
            title = stringResource(R.string.setting_tts_page_delete_provider),
            confirmText = stringResource(R.string.common_delete),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                val newProviders = settings.ttsProviders - provider
                val newSelectedId = if (settings.selectedTTSProviderId == provider.id) {
                    DEFAULT_SYSTEM_TTS_ID
                } else {
                    settings.selectedTTSProviderId
                }
                vm.updateSettings(
                    settings.copy(
                        ttsProviders = newProviders,
                        selectedTTSProviderId = newSelectedId,
                    )
                )
                showDeleteConfirm = false
                editingTTSProvider = null
            },
            onDismiss = { showDeleteConfirm = false },
        ) {
            Text(
                if (provider.name.isBlank()) {
                    stringResource(R.string.setting_tts_page_delete_unnamed_provider_confirm)
                } else {
                    stringResource(R.string.setting_tts_page_delete_provider_confirm, provider.name)
                }
            )
        }
    }

    editingASRProvider?.let { provider ->
        val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        var currentProvider by remember(provider) { mutableStateOf(provider) }
        var showDeleteConfirm by remember(provider.id) { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = {
                editingASRProvider = null
            },
            sheetState = bottomSheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_asr_page_edit_provider),
                    style = MaterialTheme.typography.headlineSmall
                )

                ASRProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f),
                    footer = {
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
                                stringResource(R.string.setting_asr_page_delete_provider),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            editingASRProvider = null
                        }
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    TextButton(
                        onClick = {
                            val newProviders = settings.asrProviders.map {
                                if (it.id == provider.id) currentProvider else it
                            }
                            vm.updateSettings(settings.copy(asrProviders = newProviders))
                            editingASRProvider = null
                        }
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
        RikkaConfirmDialog(
            show = showDeleteConfirm,
            title = stringResource(R.string.setting_asr_page_delete_provider),
            confirmText = stringResource(R.string.common_delete),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                val newProviders = settings.asrProviders - provider
                val newSelectedId = if (settings.selectedASRProviderId == provider.id) {
                    newProviders.firstOrNull()?.id
                } else {
                    settings.selectedASRProviderId
                }
                vm.updateSettings(
                    settings.copy(
                        asrProviders = newProviders,
                        selectedASRProviderId = newSelectedId,
                    )
                )
                showDeleteConfirm = false
                editingASRProvider = null
            },
            onDismiss = { showDeleteConfirm = false },
        ) {
            Text(
                if (provider.name.isBlank()) {
                    stringResource(R.string.setting_asr_page_delete_unnamed_provider_confirm)
                } else {
                    stringResource(R.string.setting_asr_page_delete_provider_confirm, provider.name)
                }
            )
        }
    }
}

@Composable
private fun TTSProviderList(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onEdit: (TTSProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateSettings(settings.copy(
            ttsProviders = settings.ttsProviders.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        ))
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = lazyListState,
    ) {
        items(settings.ttsProviders, key = { it.id }) { provider ->
            ReorderableItem(reorderableState, key = provider.id) { isDragging ->
                TTSProviderItem(
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .fillMaxWidth()
                        .longPressDraggableHandle(),
                    provider = provider,
                    isSelected = settings.selectedTTSProviderId == provider.id,
                    onSelect = {
                        onUpdateSettings(settings.copy(selectedTTSProviderId = provider.id))
                    },
                    onEdit = { onEdit(provider) },
                )
            }
        }
    }
}

@Composable
private fun ASRProviderList(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    onEdit: (ASRProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.asrProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onUpdateSettings(settings.copy(asrProviders = newProviders))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = lazyListState
    ) {
        items(settings.asrProviders, key = { it.id }) { provider ->
            ReorderableItem(
                state = reorderableState,
                key = provider.id
            ) { isDragging ->
                ASRProviderItem(
                    modifier = Modifier
                        .scale(if (isDragging) 0.95f else 1f)
                        .fillMaxWidth()
                        .longPressDraggableHandle(),
                    provider = provider,
                    isSelected = settings.selectedASRProviderId == provider.id,
                    onSelect = {
                        onUpdateSettings(settings.copy(selectedASRProviderId = provider.id))
                    },
                    onEdit = {
                        onEdit(provider)
                    }
                )
            }
        }
    }
}

@Composable
private fun AddTTSProviderButton(onAdd: (TTSProviderSetting) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentProvider: TTSProviderSetting by remember { mutableStateOf(TTSProviderSetting.SystemTTS()) }

    IconButton(
        onClick = {
            currentProvider = TTSProviderSetting.SystemTTS()
            showBottomSheet = true
        }
    ) {
        Icon(HugeIcons.Add01, stringResource(R.string.setting_tts_page_add_provider_content_description))
    }

    if (showBottomSheet) {
        val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
            },
            sheetState = bottomSheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_tts_page_add_provider),
                    style = MaterialTheme.typography.headlineSmall
                )

                TTSProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                        }
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    TextButton(
                        onClick = {
                            onAdd(currentProvider)
                            showBottomSheet = false
                        }
                    ) {
                        Text(stringResource(R.string.common_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddASRProviderButton(onAdd: (ASRProviderSetting) -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var currentProvider: ASRProviderSetting by remember { mutableStateOf(ASRProviderSetting.OpenAIRealtime()) }

    IconButton(
        onClick = {
            currentProvider = ASRProviderSetting.OpenAIRealtime()
            showBottomSheet = true
        }
    ) {
        Icon(HugeIcons.Add01, stringResource(R.string.setting_asr_page_add_provider))
    }

    if (showBottomSheet) {
        val bottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
            },
            sheetState = bottomSheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .fillMaxHeight(0.8f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_asr_page_add_provider),
                    style = MaterialTheme.typography.headlineSmall
                )

                ASRProviderConfigure(
                    setting = currentProvider,
                    onValueChange = { newState ->
                        currentProvider = newState
                    },
                    modifier = Modifier.weight(1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                        }
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }

                    TextButton(
                        onClick = {
                            onAdd(currentProvider)
                            showBottomSheet = false
                        }
                    ) {
                        Text(stringResource(R.string.common_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun TTSProviderItem(
    provider: TTSProviderSetting,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsState()

    OutlinedItemCard(
        modifier = modifier,
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
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
                    name = provider.name.ifEmpty { stringResource(R.string.setting_tts_page_default_name) },
                    modifier = Modifier.size(32.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = provider.name.ifEmpty { stringResource(R.string.setting_tts_page_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Text(
                        text = when (provider) {
                            is TTSProviderSetting.OpenAI -> stringResource(R.string.setting_tts_page_provider_openai)
                            is TTSProviderSetting.Gemini -> stringResource(R.string.setting_tts_page_provider_gemini)
                            is TTSProviderSetting.MiniMax -> "MiniMax"
                            is TTSProviderSetting.SystemTTS -> stringResource(R.string.setting_tts_page_provider_system)
                            is TTSProviderSetting.Qwen -> "Qwen"
                            is TTSProviderSetting.Groq -> "Groq"
                            is TTSProviderSetting.XAI -> "xAI"
                            is TTSProviderSetting.MiMo -> "MiMo"
                            is TTSProviderSetting.Step -> "Step"
                            is TTSProviderSetting.ElevenLabs -> "ElevenLabs"
                            is TTSProviderSetting.FishAudio -> "Fish Audio"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val testText = stringResource(R.string.setting_tts_page_test_text)
                IconButton(
                    onClick = {
                        if (!isSelected) onSelect()
                        if (!isSpeaking) {
                            tts.speak(testText)
                        } else {
                            tts.stop()
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isSelected && isSpeaking) {
                            HugeIcons.StopCircle
                        } else {
                            HugeIcons.VolumeHigh
                        },
                        contentDescription = if (isSelected && isSpeaking) {
                            stringResource(R.string.stop)
                        } else {
                            stringResource(R.string.test_tts)
                        },
                        tint = if (isSelected && isSpeaking) {
                            MaterialTheme.colorScheme.error
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
                RadioButton(selected = isSelected, onClick = onSelect)
            }
        }
    }
}

@Composable
private fun ASRProviderItem(
    provider: ASRProviderSetting,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    OutlinedItemCard(
        modifier = modifier,
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
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
                    name = provider.name.ifEmpty { stringResource(R.string.setting_asr_page_default_name) },
                    modifier = Modifier.size(32.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = provider.name.ifEmpty { stringResource(R.string.setting_asr_page_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Text(
                        text = when (provider) {
                            is ASRProviderSetting.OpenAIRealtime -> "OpenAI Realtime"
                            is ASRProviderSetting.DashScope -> "DashScope"
                            is ASRProviderSetting.Volcengine -> "Volcengine"
                            is ASRProviderSetting.MiMo -> "MiMo"
                            is ASRProviderSetting.Step -> "Step"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )
            }
        }
    }
}

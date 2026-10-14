package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import me.rerere.rikkahub.ui.components.ui.PagerTab
import me.rerere.rikkahub.ui.components.ui.PagerTabs
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.longPressReorder
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.pages.setting.components.ASRProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.ProviderEditSheet
import me.rerere.rikkahub.ui.pages.setting.components.TTSProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.typeName
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class SpeechTab(
    override val icon: ImageVector,
    @param:StringRes override val label: Int,
) : PagerTab {
    TTS(HugeIcons.VolumeHigh, R.string.speech_tab_tts),
    ASR(HugeIcons.Mic01, R.string.speech_tab_asr),
}

@Composable
fun SettingSpeechPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingTTSProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }
    var editingASRProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }
    val pagerState = rememberPagerState { SpeechTab.entries.size }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (SpeechTab.entries[pagerState.targetPage] == SpeechTab.TTS) {
                                R.string.speech_tab_tts
                            } else {
                                R.string.speech_tab_asr
                            }
                        )
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    when (SpeechTab.entries[pagerState.targetPage]) {
                        SpeechTab.TTS -> AddTTSProviderButton {
                            vm.updateSettings { latest ->
                                latest.copy(
                                    ttsProviders = listOf(it) + latest.ttsProviders
                                )
                            }
                        }

                        SpeechTab.ASR -> AddASRProviderButton {
                            vm.updateSettings { latest ->
                                latest.copy(
                                    asrProviders = listOf(it) + latest.asrProviders,
                                    selectedASRProviderId = latest.selectedASRProviderId ?: it.id
                                )
                            }
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
                tabs = SpeechTab.entries,
                modifier = Modifier.padding(bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (SpeechTab.entries[page]) {
                    SpeechTab.TTS -> TTSProviderList(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                        onEdit = { editingTTSProvider = it },
                    )

                    SpeechTab.ASR -> ASRProviderList(
                        settings = settings,
                        onUpdateSettings = vm::updateSettings,
                        onEdit = { editingASRProvider = it },
                    )
                }
            }
        }
    }

    editingTTSProvider?.let { provider ->
        var showDeleteConfirm by remember(provider.id) { mutableStateOf(false) }

        ProviderEditSheet(
            title = stringResource(R.string.setting_tts_page_edit_provider),
            initial = provider,
            confirmText = stringResource(R.string.common_save),
            onConfirm = { edited ->
                vm.updateSettings { latest ->
                    latest.copy(ttsProviders = latest.ttsProviders.map { if (it.id == provider.id) edited else it })
                }
            },
            onDismiss = { editingTTSProvider = null },
        ) { value, onValueChange, modifier ->
            TTSProviderConfigure(
                setting = value,
                onValueChange = onValueChange,
                modifier = modifier,
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
                vm.updateSettings {
                    it.copy(
                        ttsProviders = newProviders,
                        selectedTTSProviderId = newSelectedId,
                    )
                }
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
        var showDeleteConfirm by remember(provider.id) { mutableStateOf(false) }

        ProviderEditSheet(
            title = stringResource(R.string.setting_asr_page_edit_provider),
            initial = provider,
            confirmText = stringResource(R.string.common_save),
            onConfirm = { edited ->
                vm.updateSettings { latest ->
                    latest.copy(asrProviders = latest.asrProviders.map { if (it.id == provider.id) edited else it })
                }
            },
            onDismiss = { editingASRProvider = null },
        ) { value, onValueChange, modifier ->
            ASRProviderConfigure(
                setting = value,
                onValueChange = onValueChange,
                modifier = modifier,
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
                vm.updateSettings {
                    it.copy(
                        asrProviders = newProviders,
                        selectedASRProviderId = newSelectedId,
                    )
                }
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
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    onEdit: (TTSProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onUpdateSettings {
            it.copy(
                ttsProviders = it.ttsProviders.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
            )
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = lazyListState,
    ) {
        items(settings.ttsProviders, key = { it.id }) { provider ->
            ReorderableItem(reorderableState, key = provider.id) { isDragging ->
                TTSProviderItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(longPressReorder(isDragging)),
                    provider = provider,
                    isSelected = settings.selectedTTSProviderId == provider.id,
                    onSelect = {
                        onUpdateSettings { it.copy(selectedTTSProviderId = provider.id) }
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
    onUpdateSettings: ((Settings) -> Settings) -> Unit,
    onEdit: (ASRProviderSetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newProviders = settings.asrProviders.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onUpdateSettings { it.copy(asrProviders = newProviders) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                        .fillMaxWidth()
                        .then(longPressReorder(isDragging)),
                    provider = provider,
                    isSelected = settings.selectedASRProviderId == provider.id,
                    onSelect = {
                        onUpdateSettings { it.copy(selectedASRProviderId = provider.id) }
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
    var newProvider by remember { mutableStateOf<TTSProviderSetting?>(null) }

    IconButton(
        onClick = { newProvider = TTSProviderSetting.SystemTTS() }
    ) {
        Icon(HugeIcons.Add01, stringResource(R.string.setting_tts_page_add_provider_content_description))
    }

    newProvider?.let { provider ->
        ProviderEditSheet(
            title = stringResource(R.string.setting_tts_page_add_provider),
            initial = provider,
            confirmText = stringResource(R.string.common_add),
            onConfirm = onAdd,
            onDismiss = { newProvider = null },
        ) { value, onValueChange, modifier ->
            TTSProviderConfigure(setting = value, onValueChange = onValueChange, modifier = modifier)
        }
    }
}

@Composable
private fun AddASRProviderButton(onAdd: (ASRProviderSetting) -> Unit) {
    var newProvider by remember { mutableStateOf<ASRProviderSetting?>(null) }

    IconButton(
        onClick = { newProvider = ASRProviderSetting.OpenAIRealtime() }
    ) {
        Icon(HugeIcons.Add01, stringResource(R.string.setting_asr_page_add_provider))
    }

    newProvider?.let { provider ->
        ProviderEditSheet(
            title = stringResource(R.string.setting_asr_page_add_provider),
            initial = provider,
            confirmText = stringResource(R.string.common_add),
            onConfirm = onAdd,
            onDismiss = { newProvider = null },
        ) { value, onValueChange, modifier ->
            ASRProviderConfigure(setting = value, onValueChange = onValueChange, modifier = modifier)
        }
    }
}

private val TTSProviderSetting.typeName: String
    @Composable get() = when (this) {
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
        is TTSProviderSetting.Volcengine -> "火山引擎"
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
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )

    OutlinedItemCard(
        modifier = modifier,
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
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
                        text = provider.typeName,
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
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )

    OutlinedItemCard(
        modifier = modifier,
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
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
                        text = provider.typeName,
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

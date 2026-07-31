package me.rerere.rikkahub.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Play
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchResult
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.reflect.full.primaryConstructor

@Composable
fun SettingSearchPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_search_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = { nav.navigate(Screen.SettingSearchCommon) }
                    ) {
                        Icon(
                            imageVector = HugeIcons.MoreVertical,
                            contentDescription = stringResource(R.string.setting_page_search_common_options)
                        )
                    }
                    IconButton(
                        onClick = { showAddDialog = true }
                    ) {
                        Icon(
                            imageVector = HugeIcons.Add01,
                            contentDescription = stringResource(R.string.setting_page_search_add_provider)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) {
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = from.index
            val toIndex = to.index

            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < settings.searchServices.size && toIndex < settings.searchServices.size) {
                val newServices = settings.searchServices.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
                vm.updateSettings(
                    settings.copy(searchServices = newServices)
                )
            }
        }
        val haptic = LocalHapticFeedback.current

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .imePadding(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            items(settings.searchServices, key = { service -> service.id }) { service ->
                ReorderableItem(
                    state = reorderableState,
                    key = service.id
                ) { isDragging ->
                    SearchProviderCard(
                        service = service,
                        onEdit = {
                            nav.navigate(Screen.SettingSearchDetail(service.id.toString()))
                        },
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .animateItem()
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                },
                                onDragStopped = {
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                }
                            )
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { options ->
                showAddDialog = false
                vm.updateSettings(
                    settings.copy(
                        searchServices = listOf(options) + settings.searchServices
                    )
                )
                scope.launch {
                    lazyListState.animateScrollToItem(0)
                }
            }
        )
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (SearchServiceOptions) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(SearchServiceOptions.TYPES.keys.first())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.setting_page_search_add_provider))
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(SearchServiceOptions.TYPES.keys.toList()) { type ->
                    val name = SearchServiceOptions.TYPES[type] ?: "Unknown"
                    val isSelected = selectedType == type
                    Card(
                        onClick = { selectedType = type },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AutoAIIcon(
                                name = name,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val instance = selectedType.primaryConstructor!!.callBy(mapOf())
                    onConfirm(instance)
                }
            ) {
                Text(stringResource(R.string.common_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun SearchProviderCard(
    service: SearchServiceOptions,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedItemCard(
        modifier = modifier,
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AutoAIIcon(
                name = service.displayName,
                modifier = Modifier.size(32.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                SearchAbilityTagLine(options = service)
            }
        }
    }
}

@Composable
fun SearchAbilityTagLine(
    modifier: Modifier = Modifier,
    options: SearchServiceOptions
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Tag(
            type = TagType.DEFAULT,
        ) {
            Text(stringResource(R.string.search_ability_search))
        }
        if (SearchService.getService(options).scrapingParameters(options) != null) {
            Tag(
                type = TagType.DEFAULT,
            ) {
                Text(stringResource(R.string.search_ability_scrape))
            }
        }
    }
}

@Composable
internal fun CommonOptions(
    settings: me.rerere.rikkahub.data.datastore.Settings,
    onUpdate: (SearchCommonOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    var commonOptions by remember(settings.searchCommonOptions) {
        mutableStateOf(settings.searchCommonOptions)
    }
    CardGroup(
        modifier = modifier.fillMaxWidth(),
    ) {
        FormItem(
            label = {
                Text(stringResource(R.string.setting_page_search_result_size))
            }
        ) {
            OutlinedNumberInput(
                value = commonOptions.resultSize,
                onValueChange = {
                    commonOptions = commonOptions.copy(resultSize = it)
                    onUpdate(commonOptions)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (settings.searchServices.isNotEmpty()) {
            FormItem(
                label = {
                    Text(stringResource(R.string.setting_page_search_test))
                },
                content = {
                    SearchTestSection(
                        options = settings.searchServices,
                        commonOptions = commonOptions,
                    )
                },
            )
        }
    }
}

@Composable
private fun SearchTestSection(
    options: List<SearchServiceOptions>,
    commonOptions: SearchCommonOptions,
) {
    var selectedOption by remember(options) { mutableStateOf(options.first()) }
    var query by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result<SearchResult>?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Select(
            options = options,
            selectedOption = selectedOption,
            onOptionSelected = {
                selectedOption = it
                result = null
            },
            modifier = Modifier.fillMaxWidth(),
            optionToString = { it.displayName },
            optionLeading = {
                AutoAIIcon(
                    name = it.displayName,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.setting_page_search_test_query_hint)) },
                singleLine = true,
            )

            IconButton(
                onClick = {
                    if (query.isNotBlank() && !testing) {
                        testing = true
                        result = null
                        scope.launch {
                            val service = SearchService.getService(selectedOption)
                            val params = JsonObject(
                                mapOf("query" to JsonPrimitive(query))
                            )
                            result = service.search(params, commonOptions, selectedOption)
                            testing = false
                        }
                    }
                },
                enabled = query.isNotBlank() && !testing,
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = HugeIcons.Play,
                        contentDescription = stringResource(R.string.setting_page_search_test_run),
                    )
                }
            }
        }

        result?.let { res ->
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                res.onSuccess { searchResult ->
                    searchResult.answer?.let { answer ->
                        item("answer") {
                            Text(
                                text = answer,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    items(searchResult.items.size) { index ->
                        val item = searchResult.items[index]
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "${index + 1}. ${item.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = item.text.take(200),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                res.onFailure { error ->
                    item("error") {
                        Text(
                            text = error.message ?: stringResource(R.string.search_detail_unknown_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

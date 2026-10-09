package me.rerere.rikkahub.ui.pages.search

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowLeftDouble
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowRightDouble
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh01
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.fts.MessageAttachmentState
import me.rerere.rikkahub.data.db.fts.MessageSearchMode
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.bottomSheetMaxHeight
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun SearchPage(initialModelId: String? = null, vm: SearchVM = koinViewModel()) {
    val navController = LocalNavController.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val modelListState = rememberModelListState(
        modelId = null,
        providers = settings.providers,
        type = null,
    )
    val listState = rememberLazyListState()
    var showRebuildDialog by remember { mutableStateOf(false) }
    var showConditionsDialog by remember { mutableStateOf(false) }
    var draftConditions by remember { mutableStateOf(SearchConditions()) }
    // 站内首次进入自动弹一次条件弹窗；从结果跳去聊天再返回时不再重复弹出。
    var conditionsAutoOpened by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 站内正常进入搜索页时自动弹出搜索条件弹窗；带模型从外部跳转进来时不弹出。
    LaunchedEffect(Unit) {
        if (initialModelId == null && !conditionsAutoOpened) {
            draftConditions = vm.conditions
            showConditionsDialog = true
            conditionsAutoOpened = true
        }
    }

    LaunchedEffect(initialModelId, settings.providers) {
        val id = initialModelId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        if (id != null) {
            val existingModel = settings.providers.asSequence()
                .flatMap { it.models }
                .firstOrNull { it.id == id }
            vm.applyConditions(
                vm.conditions.copy(
                    selectedModel = existingModel,
                    selectedDeletedModelId = if (existingModel == null) id else null,
                    manuallyEdited = false,
                )
            )
        }
        vm.loadDeletedModelIds(
            settings.providers.asSequence().flatMap { it.models }.map { it.id }.toSet()
        )
    }

    ModelListSheet(
        state = modelListState,
        onSelect = { model ->
            draftConditions = draftConditions.copy(
                selectedModel = model,
                selectedDeletedModelId = null,
                manuallyEdited = false,
            )
        },
    )

    if (showConditionsDialog) {
        SearchConditionsDialog(
            conditions = draftConditions,
            onConditionsChange = { draftConditions = it },
            deletedModelIds = vm.deletedModelIds,
            onOpenModelList = modelListState::open,
            onDismiss = { showConditionsDialog = false },
            onConfirm = {
                showConditionsDialog = false
                vm.applyConditions(draftConditions)
            },
        )
    }

    if (showRebuildDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showRebuildDialog = false },
            title = { Text(stringResource(R.string.search_page_rebuild_index)) },
            text = { Text(stringResource(R.string.search_page_rebuild_index_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebuildDialog = false
                        vm.rebuildIndex()
                    }
                ) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { BackButton() },
                title = {
                    val collapsed = scrollBehavior.state.collapsedFraction > 0.5f
                    if (collapsed) {
                        Column {
                            Text(stringResource(R.string.search_page_title))
                            if (vm.hasSearchCriteria && !vm.isLoading && !vm.isRebuilding) {
                                Text(
                                    text = stringResource(R.string.search_page_result_count, vm.resultCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(stringResource(R.string.search_page_title))
                            if (vm.hasSearchCriteria && !vm.isLoading && !vm.isRebuilding) {
                                Text(
                                    text = stringResource(R.string.search_page_result_count, vm.resultCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showRebuildDialog = true },
                        enabled = !vm.isRebuilding,
                    ) {
                        Icon(
                            HugeIcons.Refresh01,
                            contentDescription = stringResource(R.string.search_page_rebuild_button)
                        )
                    }
                    IconButton(
                        onClick = {
                            draftConditions = vm.conditions
                            showConditionsDialog = true
                        }
                    ) {
                        Icon(
                            HugeIcons.MoreVertical,
                            contentDescription = stringResource(R.string.search_page_conditions)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        if (vm.isRebuilding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val limitToFiveSourceLines = vm.conditions.query.isBlank() && vm.conditions.hasMessageFilter
            val highlightTitle = vm.conditions.mode == MessageSearchMode.TITLE_ONLY &&
                vm.conditions.query.isNotEmpty()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                // 翻页后回到列表顶部
                LaunchedEffect(vm.currentPage) {
                    if (vm.results.isNotEmpty()) {
                        listState.scrollToItem(0)
                    }
                }

                if (vm.resultCount > 20) {
                    SearchPagination(
                        currentPage = vm.currentPage,
                        totalPages = vm.totalPages,
                        onPageChange = vm::goToPage,
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (vm.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    when {
                        !vm.hasSearchCriteria -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.search_page_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        vm.results.isEmpty() && !vm.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.search_page_no_results),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(vm.results) { result ->
                                    val assistantName = remember(result.assistantId, settings.assistants) {
                                        result.assistantId?.let { id ->
                                            settings.assistants
                                                .firstOrNull { it.id.toString() == id }
                                                ?.name
                                        }
                                    }
                                    SearchResultItem(
                                        result = result,
                                        assistantName = assistantName,
                                        query = vm.conditions.query,
                                        limitToFiveSourceLines = limitToFiveSourceLines,
                                        highlightTitle = highlightTitle,
                                        onClick = {
                                            navigateToChatPage(
                                                navController,
                                                chatId = Uuid.parse(result.conversationId),
                                                nodeId = Uuid.parse(result.nodeId),
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchConditionsDialog(
    conditions: SearchConditions,
    onConditionsChange: (SearchConditions) -> Unit,
    deletedModelIds: List<Uuid>,
    onOpenModelList: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var deletedModelsExpanded by remember { mutableStateOf(false) }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }

    val selectedModelLabel = when {
        conditions.selectedModel != null -> conditions.selectedModel.displayName
        conditions.selectedDeletedModelId != null -> conditions.selectedDeletedModelId.toString()
        conditions.manuallyEdited -> stringResource(R.string.search_page_model_manually_edited)
        else -> stringResource(R.string.common_all)
    }
    val attachmentLabel = when (conditions.attachmentState) {
        MessageAttachmentState.EXISTS -> stringResource(R.string.search_page_attachment_existing)
        MessageAttachmentState.MISSING -> stringResource(R.string.search_page_attachment_missing)
        null -> stringResource(R.string.common_all)
    }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bottomSheetMaxHeight()
                .padding(bottom = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.search_page_conditions),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            CardGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                item(
                    headlineContent = {
                        OutlinedTextField(
                            value = conditions.query,
                            onValueChange = { onConditionsChange(conditions.copy(query = it)) },
                            label = { Text(stringResource(R.string.search_page_query_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    },
                )
                FormItem(
                    label = {
                        SearchSectionLabel(stringResource(R.string.search_page_match_label))
                    },
                    content = {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            MessageSearchMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = conditions.mode == mode,
                                    onClick = {
                                        onConditionsChange(
                                            conditions.copy(
                                                mode = mode,
                                                sort = if (mode != MessageSearchMode.FUZZY &&
                                                    conditions.sort == MessageSearchSort.RELEVANCE
                                                ) {
                                                    MessageSearchSort.NEWEST_FIRST
                                                } else {
                                                    conditions.sort
                                                },
                                            )
                                        )
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = MessageSearchMode.entries.size,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = stringResource(
                                            when (mode) {
                                                MessageSearchMode.TITLE_ONLY -> R.string.search_page_mode_title
                                                MessageSearchMode.EXACT -> R.string.search_page_mode_exact
                                                MessageSearchMode.FUZZY -> R.string.search_page_mode_fuzzy
                                            }
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                )
                FormItem(
                    label = {
                        SearchSectionLabel(stringResource(R.string.search_page_sort_label))
                    },
                    content = {
                        val sortOptions = buildList {
                            add(MessageSearchSort.NEWEST_FIRST)
                            add(MessageSearchSort.OLDEST_FIRST)
                            if (conditions.mode == MessageSearchMode.FUZZY) {
                                add(MessageSearchSort.RELEVANCE)
                            }
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            sortOptions.forEachIndexed { index, sort ->
                                SegmentedButton(
                                    selected = conditions.sort == sort,
                                    onClick = { onConditionsChange(conditions.copy(sort = sort)) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = sortOptions.size,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = stringResource(
                                            when (sort) {
                                                MessageSearchSort.RELEVANCE -> R.string.search_page_sort_relevance
                                                MessageSearchSort.NEWEST_FIRST -> R.string.search_page_sort_newest
                                                MessageSearchSort.OLDEST_FIRST -> R.string.search_page_sort_oldest
                                            }
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                )
                FormItem(
                    label = {
                        SearchSectionLabel(stringResource(R.string.search_page_scope_label))
                    },
                    content = {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SearchScope.entries.forEachIndexed { index, scope ->
                                SegmentedButton(
                                    selected = conditions.scope == scope,
                                    onClick = { onConditionsChange(conditions.copy(scope = scope)) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = SearchScope.entries.size,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = stringResource(
                                            when (scope) {
                                                SearchScope.ALL_ASSISTANTS ->
                                                    R.string.search_page_scope_all_assistants
                                                SearchScope.CURRENT_ASSISTANT ->
                                                    R.string.search_page_scope_current_assistant
                                            }
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                )
                item(
                    onClick = { modelMenuExpanded = true },
                    headlineContent = { Text(stringResource(R.string.search_page_model_row)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = selectedModelLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Box {
                                DropdownMenu(
                                    expanded = modelMenuExpanded,
                                    onDismissRequest = { modelMenuExpanded = false },
                                    shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_all)) },
                                        onClick = {
                                            modelMenuExpanded = false
                                            onConditionsChange(
                                                conditions.copy(
                                                    selectedModel = null,
                                                    selectedDeletedModelId = null,
                                                    manuallyEdited = false,
                                                )
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.search_page_model_existing)) },
                                        onClick = {
                                            modelMenuExpanded = false
                                            onOpenModelList()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.search_page_model_deleted)) },
                                        onClick = {
                                            modelMenuExpanded = false
                                            deletedModelsExpanded = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.search_page_model_manually_edited))
                                        },
                                        onClick = {
                                            modelMenuExpanded = false
                                            onConditionsChange(
                                                conditions.copy(
                                                    selectedModel = null,
                                                    selectedDeletedModelId = null,
                                                    manuallyEdited = true,
                                                )
                                            )
                                        },
                                    )
                                }
                                DropdownMenu(
                                    expanded = deletedModelsExpanded,
                                    onDismissRequest = { deletedModelsExpanded = false },
                                    shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                                ) {
                                    if (deletedModelIds.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.search_page_no_deleted_models)) },
                                            enabled = false,
                                            onClick = {},
                                        )
                                    } else {
                                        deletedModelIds.forEach { modelId ->
                                            DropdownMenuItem(
                                                text = { Text(modelId.toString()) },
                                                onClick = {
                                                    deletedModelsExpanded = false
                                                    onConditionsChange(
                                                        conditions.copy(
                                                            selectedModel = null,
                                                            selectedDeletedModelId = modelId,
                                                            manuallyEdited = false,
                                                        )
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
                item(
                    onClick = { attachmentMenuExpanded = true },
                    headlineContent = { Text(stringResource(R.string.search_page_attachment_label)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = attachmentLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box {
                                DropdownMenu(
                                    expanded = attachmentMenuExpanded,
                                    onDismissRequest = { attachmentMenuExpanded = false },
                                    shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_all)) },
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            onConditionsChange(conditions.copy(attachmentState = null))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.search_page_attachment_existing))
                                        },
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            onConditionsChange(
                                                conditions.copy(attachmentState = MessageAttachmentState.EXISTS)
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.search_page_attachment_missing))
                                        },
                                        onClick = {
                                            attachmentMenuExpanded = false
                                            onConditionsChange(
                                                conditions.copy(attachmentState = MessageAttachmentState.MISSING)
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            }
        }
    }
}

@Composable
private fun SearchSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SearchPagination(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
) {
    var pageInput by remember(currentPage) { mutableStateOf(currentPage.toString()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onPageChange(1) }, enabled = currentPage > 1) {
            Icon(HugeIcons.ArrowLeftDouble, stringResource(R.string.search_page_first_page))
        }
        IconButton(onClick = { onPageChange(currentPage - 1) }, enabled = currentPage > 1) {
            Icon(HugeIcons.ArrowLeft01, stringResource(R.string.search_page_previous_page))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier
                    .width(52.dp)
                    .height(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                BasicTextField(
                    value = pageInput,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) pageInput = value
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .wrapContentHeight(Alignment.CenterVertically),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { pageInput.toIntOrNull()?.let(onPageChange) }
                    ),
                )
            }
            Text(
                text = "/$totalPages",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        IconButton(onClick = { onPageChange(currentPage + 1) }, enabled = currentPage < totalPages) {
            Icon(HugeIcons.ArrowRight01, stringResource(R.string.search_page_next_page))
        }
        IconButton(onClick = { onPageChange(totalPages) }, enabled = currentPage < totalPages) {
            Icon(HugeIcons.ArrowRightDouble, stringResource(R.string.search_page_last_page))
        }
    }
}

@Composable
private fun SearchResultItem(
    result: MessageSearchResult,
    assistantName: String?,
    query: String,
    limitToFiveSourceLines: Boolean,
    highlightTitle: Boolean,
    onClick: () -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val untitled = stringResource(R.string.conversation_untitled)
    val snippetText = buildAnnotatedString {
        val snippet = if (limitToFiveSourceLines) {
            result.snippet.split('\n').take(5).joinToString("\n")
        } else {
            result.snippet
        }
        var index = 0
        while (index < snippet.length) {
            val start = snippet.indexOf('[', index)
            if (start == -1) {
                append(snippet.substring(index))
                break
            }
            if (start > index) {
                append(snippet.substring(index, start))
            }
            val end = snippet.indexOf(']', start + 1)
            if (end == -1) {
                append(snippet.substring(start))
                break
            }
            val matched = snippet.substring(start + 1, end)
            withStyle(SpanStyle(background = highlightColor)) {
                append(matched)
            }
            index = end + 1
        }
    }
    val formattedTime = remember(result.timeAt) {
        result.timeAt.toLocalDateTime()
    }
    // 结果来源：助手/文件夹，例如 "Assistant/Folder"；缺失的部分会被省略。
    val sourceLabel = remember(assistantName, result.folderName) {
        listOfNotNull(
            assistantName?.takeIf { it.isNotBlank() },
            result.folderName?.takeIf { it.isNotBlank() },
        ).joinToString("/")
    }
    val displayTitle = result.title.ifBlank { untitled }
    val titleText = if (highlightTitle && result.title.isNotBlank() && query.isNotEmpty()) {
        buildAnnotatedString {
            var index = 0
            while (index < displayTitle.length) {
                val matchStart = displayTitle.indexOf(query, startIndex = index)
                if (matchStart < 0) {
                    append(displayTitle.substring(index))
                    break
                }
                append(displayTitle.substring(index, matchStart))
                withStyle(SpanStyle(background = highlightColor)) {
                    append(displayTitle.substring(matchStart, matchStart + query.length))
                }
                index = matchStart + query.length
            }
        }
    } else {
        buildAnnotatedString { append(displayTitle) }
    }

    OutlinedItemCard(
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (result.snippet.isNotEmpty()) {
                Text(
                    text = snippetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = if (sourceLabel.isNotEmpty()) {
                    "$formattedTime · $sourceLabel"
                } else {
                    formattedTime
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

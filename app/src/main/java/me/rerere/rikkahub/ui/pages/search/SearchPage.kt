package me.rerere.rikkahub.ui.pages.search

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Sorting01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowLeftDouble
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowRightDouble
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageAttachmentState
import me.rerere.rikkahub.data.db.fts.MessageSearchMode
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.SearchFieldShape
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.plus
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
    val focusRequester = remember { FocusRequester() }
    var showRebuildDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(initialModelId, settings.providers) {
        val id = initialModelId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        if (id != null) {
            val existingModel = settings.providers.asSequence()
                .flatMap { it.models }
                .firstOrNull { it.id == id }
            if (existingModel != null) {
                vm.onModelSearch(existingModel)
            } else {
                vm.onDeletedModelSearch(id)
            }
        }
        vm.loadDeletedModelIds(
            settings.providers.asSequence().flatMap { it.models }.map { it.id }.toSet()
        )
    }

    ModelListSheet(state = modelListState, onSelect = vm::onModelSearch)

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
                },
                actions = {
                    SearchModeMenuButton(
                        current = vm.searchMode,
                        onModeChange = vm::onSearchModeChange,
                        onModelSearch = modelListState::open,
                        deletedModelIds = vm.deletedModelIds,
                        onDeletedModelSearch = vm::onDeletedModelSearch,
                        onManuallyEditedMessagesSearch = vm::onManuallyEditedMessagesSearch,
                        onAttachmentSearch = vm::onAttachmentSearch,
                    )
                    SortMenuButton(
                        current = vm.sortOrder,
                        allowRelevance = vm.searchMode == MessageSearchMode.FUZZY &&
                            !vm.isModelFilteredSearch,
                        onSortChange = { vm.onSortChange(it) },
                    )
                    IconButton(
                        onClick = { showRebuildDialog = true },
                        enabled = !vm.isRebuilding,
                    ) {
                        Icon(
                            HugeIcons.Refresh01,
                            contentDescription = stringResource(R.string.search_page_rebuild_button)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.onQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        when {
                            vm.selectedModel != null -> stringResource(
                                R.string.search_page_model_selected,
                                vm.selectedModel!!.displayName,
                            )
                            vm.selectedDeletedModelId != null -> stringResource(
                                R.string.search_page_deleted_model_selected,
                                vm.selectedDeletedModelId.toString(),
                            )
                            vm.searchManuallyEditedMessages -> stringResource(
                                R.string.search_page_manually_edited_selected
                            )
                            vm.attachmentState == MessageAttachmentState.EXISTS -> stringResource(
                                R.string.search_page_attachment_existing_selected
                            )
                            vm.attachmentState == MessageAttachmentState.MISSING -> stringResource(
                                R.string.search_page_attachment_missing_selected
                            )
                            else -> stringResource(R.string.search_page_placeholder)
                        }
                    )
                },
                shape = SearchFieldShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { vm.search() }
                ),
                )

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
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(vm.results) { result ->
                                SearchResultItem(
                                    result = result,
                                    query = vm.searchQuery,
                                    limitToFiveSourceLines = vm.isModelFilteredSearch,
                                    highlightTitle = !vm.isModelFilteredSearch &&
                                        vm.searchMode == MessageSearchMode.TITLE_ONLY,
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
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
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
private fun SearchModeMenuButton(
    current: MessageSearchMode,
    onModeChange: (MessageSearchMode) -> Unit,
    onModelSearch: () -> Unit,
    deletedModelIds: List<Uuid>,
    onDeletedModelSearch: (Uuid) -> Unit,
    onManuallyEditedMessagesSearch: () -> Unit,
    onAttachmentSearch: (MessageAttachmentState) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var modelOptionsExpanded by remember { mutableStateOf(false) }
    var deletedModelsExpanded by remember { mutableStateOf(false) }
    var attachmentOptionsExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                HugeIcons.Search01,
                contentDescription = stringResource(R.string.search_page_mode),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
        ) {
            MessageSearchMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (mode) {
                                    MessageSearchMode.TITLE_ONLY -> R.string.search_page_mode_title
                                    MessageSearchMode.EXACT -> R.string.search_page_mode_exact
                                    MessageSearchMode.FUZZY -> R.string.search_page_mode_fuzzy
                                }
                            )
                        )
                    },
                    leadingIcon = {
                        RadioButton(selected = mode == current, onClick = null)
                    },
                    onClick = {
                        expanded = false
                        onModeChange(mode)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_page_mode_model)) },
                onClick = {
                    expanded = false
                    modelOptionsExpanded = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_page_mode_attachment)) },
                onClick = {
                    expanded = false
                    attachmentOptionsExpanded = true
                },
            )
        }
        DropdownMenu(
            expanded = modelOptionsExpanded,
            onDismissRequest = { modelOptionsExpanded = false },
            shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_page_model_existing)) },
                onClick = {
                    modelOptionsExpanded = false
                    onModelSearch()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_page_model_deleted)) },
                onClick = {
                    modelOptionsExpanded = false
                    deletedModelsExpanded = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_page_model_manually_edited)) },
                onClick = {
                    modelOptionsExpanded = false
                    onManuallyEditedMessagesSearch()
                },
            )
        }
        DropdownMenu(
            expanded = attachmentOptionsExpanded,
            onDismissRequest = { attachmentOptionsExpanded = false },
            shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_page_attachment_existing)) },
                onClick = {
                    attachmentOptionsExpanded = false
                    onAttachmentSearch(MessageAttachmentState.EXISTS)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_page_attachment_missing)) },
                onClick = {
                    attachmentOptionsExpanded = false
                    onAttachmentSearch(MessageAttachmentState.MISSING)
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
                            onDeletedModelSearch(modelId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenuButton(
    current: MessageSearchSort,
    allowRelevance: Boolean,
    onSortChange: (MessageSearchSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                HugeIcons.Sorting01,
                contentDescription = stringResource(R.string.search_page_sort)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
        ) {
            MessageSearchSort.entries
                .filter { it != MessageSearchSort.RELEVANCE || allowRelevance }
                .forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (sort) {
                                    MessageSearchSort.RELEVANCE -> R.string.search_page_sort_relevance
                                    MessageSearchSort.NEWEST_FIRST -> R.string.search_page_sort_newest
                                    MessageSearchSort.OLDEST_FIRST -> R.string.search_page_sort_oldest
                                }
                            )
                        )
                    },
                    leadingIcon = {
                        RadioButton(
                            selected = sort == current,
                            onClick = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortChange(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: MessageSearchResult,
    query: String,
    limitToFiveSourceLines: Boolean,
    highlightTitle: Boolean,
    onClick: () -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val untitled = stringResource(R.string.search_page_untitled)
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
    val formattedTime = remember(result.updateAt) {
        result.updateAt.toLocalDateTime()
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
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package me.rerere.rikkahub.ui.pages.extensions

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.FileImport
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Cancel01
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.export.LorebookSerializer
import me.rerere.rikkahub.data.export.ModeInjectionSerializer
import me.rerere.rikkahub.data.export.rememberExporter
import me.rerere.rikkahub.data.export.rememberImporter
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ExportDialog
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.TextArea
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ModeInjectionPage(vm: PromptVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { BackButton() },
                title = { Text(stringResource(R.string.prompt_page_mode_injection_tab)) },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ModeInjectionTab(
                modeInjections = settings.modeInjections,
                onUpdate = { vm.updateSettings(settings.copy(modeInjections = it)) },
            )
        }
    }
}

@Composable
fun LorebookPage(vm: PromptVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = { BackButton() },
                title = { Text(stringResource(R.string.prompt_page_lorebook_tab)) },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LorebookTab(
                lorebooks = settings.lorebooks,
                onUpdate = { vm.updateSettings(settings.copy(lorebooks = it)) },
            )
        }
    }
}

@Composable
private fun ModeInjectionTab(
    modeInjections: List<PromptInjection.ModeInjection>,
    onUpdate: (List<PromptInjection.ModeInjection>) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val toaster = LocalToaster.current
    val currentModeInjections by rememberUpdatedState(modeInjections)
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = modeInjections.toMutableList()
        val item = newList.removeAt(from.index)
        newList.add(to.index, item)
        onUpdate(newList)
    }
    val editState = useEditState<PromptInjection.ModeInjection> { edited ->
        val index = modeInjections.indexOfFirst { it.id == edited.id }
        if (index >= 0) {
            onUpdate(modeInjections.toMutableList().apply { set(index, edited) })
        } else {
            onUpdate(modeInjections + edited)
        }
    }
    val importSuccessMsg = stringResource(R.string.export_import_success)
    val importFailedMsg = stringResource(R.string.export_import_failed)
    val importer = rememberImporter(ModeInjectionSerializer) { result ->
        result.onSuccess { imported ->
            onUpdate(currentModeInjections + imported)
            toaster.show(importSuccessMsg)
        }.onFailure { error ->
            toaster.show(importFailedMsg.format(error.message), type = ToastType.Error)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false }
                ),
            contentPadding = PaddingValues(8.dp) + PaddingValues(bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            if (modeInjections.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight(0.8f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_page_mode_injection_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.prompt_page_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                items(modeInjections, key = { it.id }) { injection ->
                    ReorderableItem(
                        state = reorderableState,
                        key = injection.id
                    ) { isDragging ->
                        ModeInjectionCard(
                            injection = injection,
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .graphicsLayer {
                                    if (isDragging) {
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                    }
                            },
                            onEdit = { editState.open(injection) },
                        )
                    }
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
            leadingContent = {
                IconButton(onClick = { importer.importFromFile() }) {
                    Icon(HugeIcons.FileImport, null)
                }
            },
        ) {
            Button(onClick = { editState.open(PromptInjection.ModeInjection()) }) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(HugeIcons.Add01, null)
                    AnimatedVisibility(expanded) {
                        Row {
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.prompt_page_add_mode_injection))
                        }
                    }
                }
            }
        }
    }

    if (editState.isEditing) {
        editState.currentState?.let { state ->
            ModeInjectionEditSheet(
                injection = state,
                onDismiss = { editState.dismiss() },
                onConfirm = { editState.confirm() },
                onEdit = { editState.currentState = it },
                onDelete = modeInjections
                    .firstOrNull { it.id == state.id }
                    ?.let { original ->
                        {
                            onUpdate(modeInjections - original)
                            editState.dismiss()
                        }
                    },
            )
        }
    }
}

@Composable
private fun ModeInjectionCard(
    injection: PromptInjection.ModeInjection,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    OutlinedItemCard(
        modifier = modifier,
        onClick = onEdit,
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = injection.name.ifEmpty { stringResource(R.string.prompt_page_unnamed) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Tag(type = TagType.INFO) {
                            Text(getPositionLabel(injection.position))
                        }
                        Tag(type = TagType.DEFAULT) {
                            Text(stringResource(R.string.prompt_page_priority_format, injection.priority))
                        }
                        if (!injection.enabled) {
                            Tag(type = TagType.WARNING) {
                                Text(stringResource(R.string.prompt_page_disabled))
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun ModeInjectionEditSheet(
    injection: PromptInjection.ModeInjection,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: (PromptInjection.ModeInjection) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val exporter = rememberExporter(injection, ModeInjectionSerializer)
    val contentState = rememberTextFieldState(injection.content)
    val currentInjection by rememberUpdatedState(injection)
    LaunchedEffect(injection.id) {
        snapshotFlow { contentState.text.toString() }.collect { content ->
            if (content != currentInjection.content) {
                onEdit(currentInjection.copy(content = content))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(onClick = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }) {
                Icon(HugeIcons.ArrowDown01, null)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.prompt_page_edit_mode_injection),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CardGroup(modifier = Modifier.fillMaxWidth()) {
                    item(
                        supportingContent = {
                            OutlinedTextField(
                                value = injection.name,
                                onValueChange = { onEdit(injection.copy(name = it)) },
                                label = { Text(stringResource(R.string.prompt_page_name)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        headlineContent = {},
                    )

                    FormItem(
                        label = { Text(stringResource(R.string.prompt_page_enabled)) },
                        tail = {
                            Switch(
                                checked = injection.enabled,
                                onCheckedChange = { onEdit(injection.copy(enabled = it)) }
                            )
                        }
                    )

                    item(
                        supportingContent = {
                            OutlinedTextField(
                                value = injection.priority.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { p -> onEdit(injection.copy(priority = p)) }
                                },
                                label = { Text(stringResource(R.string.prompt_page_priority_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        },
                        headlineContent = {},
                    )

                    item(
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.prompt_page_injection_position),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                InjectionPositionSelector(
                                    position = injection.position,
                                    onSelect = { onEdit(injection.copy(position = it)) }
                                )
                            }
                        },
                        headlineContent = {},
                    )

                    if (injection.position == InjectionPosition.AT_DEPTH) {
                        item(
                            supportingContent = {
                                OutlinedTextField(
                                    value = injection.injectDepth.toString(),
                                    onValueChange = {
                                        it.toIntOrNull()?.let { d -> onEdit(injection.copy(injectDepth = d)) }
                                    },
                                    label = { Text(stringResource(R.string.prompt_page_inject_depth)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            },
                            headlineContent = {},
                        )
                    }

                    if (injection.position.usesStandaloneMessage()) {
                        item(
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.prompt_page_injection_role),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    InjectionRoleSelector(
                                        role = injection.role,
                                        onSelect = { onEdit(injection.copy(role = it)) }
                                    )
                                }
                            },
                            headlineContent = {},
                        )
                    }

                    item(
                        supportingContent = {
                            TextArea(
                                state = contentState,
                                label = stringResource(R.string.prompt_page_injection_content),
                                labelInField = true,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                maxLines = Int.MAX_VALUE,
                                enableImport = false,
                                enableFullscreen = false,
                            )
                        },
                        headlineContent = {},
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(HugeIcons.Delete01, stringResource(R.string.common_delete))
                    }
                }
                IconButton(onClick = { showExportDialog = true }) {
                    Icon(HugeIcons.Share01, stringResource(R.string.common_export))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            }
        }
    }
    if (showExportDialog) {
        ExportDialog(exporter = exporter, onDismiss = { showExportDialog = false })
    }
    if (showDeleteConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.prompt_page_delete_mode_injection_title)) },
            text = {
                Text(
                    if (injection.name.isBlank()) {
                        stringResource(R.string.prompt_page_delete_unnamed_mode_injection_confirm)
                    } else {
                        stringResource(
                            R.string.prompt_page_delete_mode_injection_confirm,
                            injection.name,
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    }
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun InjectionPositionSelector(
    position: InjectionPosition,
    onSelect: (InjectionPosition) -> Unit
) {
    Select(
        options = InjectionPosition.entries,
        selectedOption = position,
        onOptionSelected = onSelect,
        optionToString = { getPositionLabel(it) },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun InjectionPosition.usesStandaloneMessage(): Boolean = when (this) {
    InjectionPosition.BEFORE_SYSTEM_PROMPT,
    InjectionPosition.AFTER_SYSTEM_PROMPT -> false

    InjectionPosition.TOP_OF_CHAT,
    InjectionPosition.BOTTOM_OF_CHAT,
    InjectionPosition.AT_DEPTH -> true
}

@Composable
private fun getPositionLabel(position: InjectionPosition): String = when (position) {
    InjectionPosition.BEFORE_SYSTEM_PROMPT -> stringResource(R.string.prompt_page_position_before_system)
    InjectionPosition.AFTER_SYSTEM_PROMPT -> stringResource(R.string.prompt_page_position_after_system)
    InjectionPosition.TOP_OF_CHAT -> stringResource(R.string.prompt_page_position_top_of_chat)
    InjectionPosition.BOTTOM_OF_CHAT -> stringResource(R.string.prompt_page_position_bottom_of_chat)
    InjectionPosition.AT_DEPTH -> stringResource(R.string.prompt_page_position_at_depth)
}

@Composable
private fun InjectionRoleSelector(
    role: MessageRole,
    onSelect: (MessageRole) -> Unit
) {
    Select(
        options = listOf(MessageRole.USER, MessageRole.ASSISTANT),
        selectedOption = role,
        onOptionSelected = onSelect,
        optionToString = { getRoleLabel(it) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun getRoleLabel(role: MessageRole): String = when (role) {
    MessageRole.USER -> stringResource(R.string.prompt_page_role_user)
    MessageRole.ASSISTANT -> stringResource(R.string.prompt_page_role_assistant)
    else -> role.name
}

// ==================== Lorebook Tab ====================

@Composable
private fun LorebookTab(
    lorebooks: List<Lorebook>,
    onUpdate: (List<Lorebook>) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val lazyListState = rememberLazyListState()
    val toaster = LocalToaster.current
    val currentLorebooks by rememberUpdatedState(lorebooks)
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val newList = lorebooks.toMutableList()
        val item = newList.removeAt(from.index)
        newList.add(to.index, item)
        onUpdate(newList)
    }
    val editState = useEditState<Lorebook> { edited ->
        val index = lorebooks.indexOfFirst { it.id == edited.id }
        if (index >= 0) {
            onUpdate(lorebooks.toMutableList().apply { set(index, edited) })
        } else {
            onUpdate(lorebooks + edited)
        }
    }
    val importSuccessMsg = stringResource(R.string.export_import_success)
    val importFailedMsg = stringResource(R.string.export_import_failed)
    val importer = rememberImporter(LorebookSerializer) { result ->
        result.onSuccess { imported ->
            onUpdate(currentLorebooks + imported)
            toaster.show(importSuccessMsg)
        }.onFailure { error ->
            toaster.show(importFailedMsg.format(error.message), type = ToastType.Error)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false }
                ),
            contentPadding = PaddingValues(8.dp) + PaddingValues(bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            if (lorebooks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight(0.8f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_page_lorebook_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.prompt_page_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                items(lorebooks, key = { it.id }) { book ->
                    ReorderableItem(
                        state = reorderableState,
                        key = book.id
                    ) { isDragging ->
                        LorebookCard(
                            book = book,
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .graphicsLayer {
                                    if (isDragging) {
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                    }
                            },
                            onEdit = { editState.open(book) },
                        )
                    }
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
            leadingContent = {
                IconButton(onClick = { importer.importFromFile() }) {
                    Icon(HugeIcons.FileImport, null)
                }
            },
        ) {
            Button(onClick = { editState.open(Lorebook()) }) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(HugeIcons.Add01, null)
                    AnimatedVisibility(expanded) {
                        Row {
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.prompt_page_add_lorebook))
                        }
                    }
                }
            }
        }
    }

    if (editState.isEditing) {
        editState.currentState?.let { state ->
            LorebookEditSheet(
                book = state,
                onDismiss = { editState.dismiss() },
                onConfirm = { editState.confirm() },
                onEdit = { editState.currentState = it },
                onDelete = lorebooks
                    .firstOrNull { it.id == state.id }
                    ?.let { original ->
                        {
                            onUpdate(lorebooks - original)
                            editState.dismiss()
                        }
                    },
            )
        }
    }
}

@Composable
private fun LorebookCard(
    book: Lorebook,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    OutlinedItemCard(
        modifier = modifier,
        onClick = onEdit,
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = book.name.ifEmpty { stringResource(R.string.prompt_page_unnamed_lorebook) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (book.description.isNotEmpty()) {
                        Text(
                            text = book.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Tag(type = TagType.INFO) {
                            Text(
                                stringResource(
                                    R.string.prompt_page_entries_count_format,
                                    book.entries.size
                                )
                            )
                        }
                        if (!book.enabled) {
                            Tag(type = TagType.WARNING) {
                                Text(stringResource(R.string.prompt_page_disabled))
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun LorebookEditSheet(
    book: Lorebook,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: (Lorebook) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val exporter = rememberExporter(book, LorebookSerializer)
    val entryEditState = useEditState<PromptInjection.RegexInjection> { edited ->
        val index = book.entries.indexOfFirst { it.id == edited.id }
        if (index >= 0) {
            onEdit(book.copy(entries = book.entries.toMutableList().apply { set(index, edited) }))
        } else {
            onEdit(book.copy(entries = book.entries + edited))
        }
    }
    val entryListState = rememberLazyListState()
    val entryReorderableState = rememberReorderableLazyListState(entryListState) { from, to ->
        val reordered = book.entries.toMutableList()
        val item = reordered.removeAt(from.index)
        reordered.add(to.index, item)
        onEdit(book.copy(entries = reordered))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(onClick = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }) {
                Icon(HugeIcons.ArrowDown01, null)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.prompt_page_edit_lorebook),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CardGroup {
                    item(
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(
                                    value = book.name,
                                    onValueChange = { onEdit(book.copy(name = it)) },
                                    label = { Text(stringResource(R.string.prompt_page_name)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = book.description,
                                    onValueChange = { onEdit(book.copy(description = it)) },
                                    label = { Text(stringResource(R.string.prompt_page_description)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        headlineContent = {},
                    )

                    FormItem(
                        label = { Text(stringResource(R.string.prompt_page_enabled)) },
                        tail = {
                            Switch(
                                checked = book.enabled,
                                onCheckedChange = { onEdit(book.copy(enabled = it)) }
                            )
                        }
                    )
                }

                // 条目列表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.prompt_page_entries_format, book.entries.size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(onClick = {
                        entryEditState.open(PromptInjection.RegexInjection())
                    }) {
                        Icon(HugeIcons.Add01, stringResource(R.string.prompt_page_add_entry))
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((book.entries.size * 80).coerceAtMost(360).dp),
                    state = entryListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(book.entries, key = { it.id }) { entry ->
                        ReorderableItem(
                            state = entryReorderableState,
                            key = entry.id,
                        ) { isDragging ->
                            RegexInjectionEntryCard(
                                entry = entry,
                                onEdit = { entryEditState.open(entry) },
                                modifier = Modifier
                                    .longPressDraggableHandle()
                                    .graphicsLayer {
                                        if (isDragging) {
                                            scaleX = 1.05f
                                            scaleY = 1.05f
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
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(HugeIcons.Delete01, stringResource(R.string.common_delete))
                    }
                }
                IconButton(onClick = { showExportDialog = true }) {
                    Icon(HugeIcons.Share01, stringResource(R.string.common_export))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            }
        }
    }
    if (showExportDialog) {
        ExportDialog(exporter = exporter, onDismiss = { showExportDialog = false })
    }
    if (showDeleteConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.prompt_page_delete_lorebook_title)) },
            text = {
                Text(
                    if (book.name.isBlank()) {
                        stringResource(R.string.prompt_page_delete_unnamed_lorebook_confirm)
                    } else {
                        stringResource(R.string.prompt_page_delete_lorebook_confirm, book.name)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    }
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (entryEditState.isEditing) {
        entryEditState.currentState?.let { state ->
            RegexInjectionEditDialog(
                entry = state,
                onDismiss = { entryEditState.dismiss() },
                onConfirm = { entryEditState.confirm() },
                onEdit = { entryEditState.currentState = it },
                onDelete = book.entries
                    .firstOrNull { it.id == state.id }
                    ?.let { original ->
                        {
                            onEdit(book.copy(entries = book.entries - original))
                            entryEditState.dismiss()
                        }
                    },
            )
        }
    }
}

@Composable
private fun RegexInjectionEntryCard(
    entry: PromptInjection.RegexInjection,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    OutlinedItemCard(
        modifier = modifier,
        onClick = onEdit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.name.ifEmpty { stringResource(R.string.prompt_page_unnamed_entry) },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (entry.keywords.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.prompt_page_keywords_format, entry.keywords.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!entry.enabled) {
                        Tag(type = TagType.WARNING) {
                            Text(stringResource(R.string.prompt_page_disabled))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegexInjectionEditDialog(
    entry: PromptInjection.RegexInjection,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: (PromptInjection.RegexInjection) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var newKeyword by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val contentState = rememberTextFieldState(entry.content)
    val currentEntry by rememberUpdatedState(entry)

    LaunchedEffect(entry.id) {
        snapshotFlow { contentState.text.toString() }
            .collect { content ->
                if (content != currentEntry.content) {
                    onEdit(currentEntry.copy(content = content))
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(onClick = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }) {
                Icon(HugeIcons.ArrowDown01, null)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.prompt_page_edit_entry),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CardGroup(modifier = Modifier.fillMaxWidth()) {
                    item(
                        supportingContent = {
                            OutlinedTextField(
                                value = entry.name,
                                onValueChange = { onEdit(entry.copy(name = it)) },
                                label = { Text(stringResource(R.string.prompt_page_name)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        headlineContent = {},
                    )

                    FormItem(
                        label = { Text(stringResource(R.string.prompt_page_enabled)) },
                        tail = {
                            Switch(
                                checked = entry.enabled,
                                onCheckedChange = { onEdit(entry.copy(enabled = it)) }
                            )
                        }
                    )

                    item(
                        supportingContent = {
                            OutlinedTextField(
                                value = entry.priority.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { p -> onEdit(entry.copy(priority = p)) }
                                },
                                label = { Text(stringResource(R.string.prompt_page_priority_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        },
                        headlineContent = {},
                    )

                    item(
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.prompt_page_injection_position),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                InjectionPositionSelector(
                                    position = entry.position,
                                    onSelect = { onEdit(entry.copy(position = it)) }
                                )
                            }
                        },
                        headlineContent = {},
                    )

                    if (entry.position == InjectionPosition.AT_DEPTH) {
                        item(
                            supportingContent = {
                                OutlinedTextField(
                                    value = entry.injectDepth.toString(),
                                    onValueChange = {
                                        it.toIntOrNull()?.let { d -> onEdit(entry.copy(injectDepth = d)) }
                                    },
                                    label = { Text(stringResource(R.string.prompt_page_inject_depth)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            },
                            headlineContent = {},
                        )
                    }

                    item(
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.prompt_page_keywords_label),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    entry.keywords.forEach { keyword ->
                                        InputChip(
                                            selected = false,
                                            onClick = {},
                                            label = { Text(keyword) },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        onEdit(entry.copy(keywords = entry.keywords - keyword))
                                                    },
                                                    modifier = Modifier.size(16.dp),
                                                ) {
                                                    Icon(HugeIcons.Cancel01, null, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = newKeyword,
                                        onValueChange = { newKeyword = it },
                                        label = { Text(stringResource(R.string.prompt_page_new_keyword)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                    )
                                    IconButton(
                                        onClick = {
                                            if (newKeyword.isNotBlank()) {
                                                onEdit(entry.copy(keywords = entry.keywords + newKeyword.trim()))
                                                newKeyword = ""
                                            }
                                        }
                                    ) {
                                        Icon(HugeIcons.Add01, stringResource(R.string.common_add))
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.prompt_page_new_keyword_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        headlineContent = {},
                    )

                    FormItem(
                        label = { Text(stringResource(R.string.prompt_page_use_regex)) },
                        tail = {
                            Switch(
                                checked = entry.useRegex,
                                onCheckedChange = { onEdit(entry.copy(useRegex = it)) }
                            )
                        }
                    )

                    FormItem(
                        label = { Text(stringResource(R.string.prompt_page_case_sensitive)) },
                        tail = {
                            Switch(
                                checked = entry.caseSensitive,
                                onCheckedChange = { onEdit(entry.copy(caseSensitive = it)) }
                            )
                        }
                    )

                    FormItem(
                        label = { Text(stringResource(R.string.prompt_page_constant_active)) },
                        description = { Text(stringResource(R.string.prompt_page_constant_active_desc)) },
                        tail = {
                            Switch(
                                checked = entry.constantActive,
                                onCheckedChange = { onEdit(entry.copy(constantActive = it)) }
                            )
                        }
                    )

                    item(
                        supportingContent = {
                            OutlinedTextField(
                                value = entry.scanDepth.toString(),
                                onValueChange = {
                                    it.toIntOrNull()?.let { d -> onEdit(entry.copy(scanDepth = d)) }
                                },
                                label = { Text(stringResource(R.string.prompt_page_scan_depth)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        },
                        headlineContent = {},
                    )

                    if (entry.position.usesStandaloneMessage()) {
                        item(
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.prompt_page_injection_role),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    InjectionRoleSelector(
                                        role = entry.role,
                                        onSelect = { onEdit(entry.copy(role = it)) }
                                    )
                                }
                            },
                            headlineContent = {},
                        )
                    }

                    item(
                        supportingContent = {
                            TextArea(
                                state = contentState,
                                label = stringResource(R.string.prompt_page_injection_content),
                                labelInField = true,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                maxLines = Int.MAX_VALUE,
                                enableImport = false,
                                enableFullscreen = false,
                            )
                        },
                        headlineContent = {},
                    )
                }

                if (onDelete != null) {
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
                            stringResource(R.string.prompt_page_delete_entry),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            val canSave = entry.keywords.isNotEmpty() || entry.constantActive
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    onClick = onConfirm,
                    enabled = canSave,
                ) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.prompt_page_delete_entry)) },
            text = { Text(stringResource(R.string.prompt_page_delete_entry_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

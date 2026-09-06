package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.export.QuickMessageSerializer
import me.rerere.rikkahub.data.export.rememberExporter
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.ExportDialog
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QuickMessagesPage(vm: QuickMessagesVM = koinViewModel()) {
    val settings = vm.settings.collectAsStateWithLifecycle().value
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<QuickMessage?>(null) }
    var deleteTarget by remember { mutableStateOf<QuickMessage?>(null) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        vm.reorderQuickMessages(settings.quickMessages.toMutableList().apply {
            add(to.index, removeAt(from.index))
        })
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.assistant_page_quick_messages)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.Add01, contentDescription = null)
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.quickMessages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.Zap,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.quick_messages_page_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.quick_messages_page_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(settings.quickMessages, key = { it.id }) { quickMessage ->
                ReorderableItem(reorderableState, key = quickMessage.id) {
                    QuickMessageCard(
                        quickMessage = quickMessage,
                        onEdit = { editTarget = quickMessage },
                        modifier = Modifier.longPressDraggableHandle(),
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        EditQuickMessageDialog(
            title = stringResource(R.string.quick_messages_page_add_title),
            initialQuickMessage = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, content ->
                vm.addQuickMessage(title, content)
                showAddDialog = false
            },
        )
    }

    editTarget?.let { quickMessage ->
        EditQuickMessageDialog(
            title = stringResource(R.string.quick_messages_page_edit_title),
            initialQuickMessage = quickMessage,
            onDismiss = { editTarget = null },
            onConfirm = { title, content ->
                vm.updateQuickMessage(
                    quickMessage.copy(
                        title = title,
                        content = content,
                    )
                )
                editTarget = null
            },
            onDelete = {
                editTarget = null
                deleteTarget = quickMessage
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.quick_messages_page_delete_title),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            deleteTarget?.let { vm.deleteQuickMessage(it.id) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        val targetTitle = deleteTarget?.title.orEmpty()
        Text(
            if (targetTitle.isBlank()) {
                stringResource(R.string.quick_messages_page_delete_unnamed_message)
            } else {
                stringResource(R.string.quick_messages_page_delete_message, targetTitle)
            }
        )
    }
}

@Composable
private fun QuickMessageCard(
    quickMessage: QuickMessage,
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
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = HugeIcons.Zap,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = quickMessage.title.ifBlank { stringResource(R.string.quick_messages_page_untitled) },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = quickMessage.content.ifBlank { stringResource(R.string.quick_messages_page_empty_content) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditQuickMessageDialog(
    title: String,
    initialQuickMessage: QuickMessage?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var quickMessageTitle by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.title ?: "")
    }
    var quickMessageContent by rememberSaveable(initialQuickMessage?.id) {
        mutableStateOf(initialQuickMessage?.content ?: "")
    }
    var showExportDialog by remember { mutableStateOf(false) }
    val exporter = initialQuickMessage?.let { rememberExporter(it, QuickMessageSerializer) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = quickMessageTitle,
                        onValueChange = { quickMessageTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.assistant_page_quick_message_title)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = quickMessageContent,
                        onValueChange = { quickMessageContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.assistant_page_quick_message_content)) },
                        minLines = 4,
                        maxLines = Int.MAX_VALUE,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                HugeIcons.Delete01,
                                contentDescription = stringResource(R.string.common_delete),
                            )
                        }
                        IconButton(onClick = { showExportDialog = true }, modifier = Modifier.padding(start = 12.dp)) {
                            Icon(
                                imageVector = HugeIcons.Share01,
                                contentDescription = stringResource(R.string.common_export),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    TextButton(
                        onClick = { onConfirm(quickMessageTitle.trim(), quickMessageContent.trim()) },
                        enabled = quickMessageTitle.isNotBlank() && quickMessageContent.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }
    }

    if (showExportDialog && exporter != null) {
        ExportDialog(exporter = exporter, onDismiss = { showExportDialog = false })
    }
}
